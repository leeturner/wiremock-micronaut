"""The upstream streams a gig's setlist as server-sent events.

mappings/live.json opens the SSE channel and declares a "start" stub;
message-mappings/live-setlist.json sends the songs when that stub is hit.
"""

import time
from typing import Annotated

from com.github.tomakehurst.wiremock import WireMockServer
from com.github.tomakehurst.wiremock.client import WireMock
from com.github.tomakehurst.wiremock.message import MessagePattern
from com.leeturner.wiremock.micronaut import ConfigureWireMock, EnableWireMock
from java.net import URI
from java.net.http import HttpClient, HttpRequest, HttpResponse
from java.time import Duration
from java.util.concurrent import TimeUnit
from jakarta.inject import Inject, Named
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.live_setlist_service import LiveSetlistService

MicronautTest()
EnableWireMock(
    ConfigureWireMock(
        name="live-setlist",
        baseUrlProperties=["micronaut.http.services.live-setlist.url"],
        filesUnderClasspath="stubs-by-server/live-setlist",
        registerBean=True,
    )
)

service: Annotated[LiveSetlistService, Inject]
live_setlist: Annotated[WireMockServer, Inject, Named("live-setlist")]

GIG = "radiohead-2026"


@Test
def test_streams_songs_until_the_gig_ends():
    channels_before = live_setlist.listAllMessageChannels().getChannels().size()
    songs = service.songs(GIG).collectList().toFuture()

    await_channel_count(channels_before + 1)
    HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create(f"{live_setlist.baseUrl()}/gigs/{GIG}/start"))
        .POST(HttpRequest.BodyPublishers.noBody())
        .build(),
        HttpResponse.BodyHandlers.discarding(),
    )

    assert list(songs.get(5, TimeUnit.SECONDS)) == ["Airbag", "Paranoid Android"]
    assert live_setlist.waitForMessageEvent(
        MessagePattern.messagePattern().withBody(WireMock.equalTo("encore over")).build(),
        Duration.ofSeconds(5),
    ).isPresent()


def await_channel_count(count):
    """Trigger only once the app is connected: events sent before then have no
    channel. Count channels rather than checking for any, as disconnected ones
    can stay listed.
    """
    deadline = time.monotonic() + 5
    while live_setlist.listAllMessageChannels().getChannels().size() < count:
        if time.monotonic() > deadline:
            raise AssertionError("The app never opened the SSE stream")
        time.sleep(0.02)
