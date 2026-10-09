"""Every stub is declared in the test.

Neither server configures files, so both also load the default directory
(wiremock/); stubs added here win because WireMock prefers the most recently
added stub. Pyronaut only injects module attributes marked Inject, so the
servers are registered as beans and injected by name.
"""

from typing import Annotated

from com.github.tomakehurst.wiremock import WireMockServer
from com.github.tomakehurst.wiremock.client import WireMock
from com.github.tomakehurst.wiremock.http import Fault
from com.leeturner.wiremock.micronaut import ConfigureWireMock, EnableWireMock
from jakarta.inject import Inject, Named
from micronaut.context import ApplicationContext
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import BeforeEach, Test
from pyronaut import requests

MicronautTest()
EnableWireMock(
    [
        ConfigureWireMock(name="musicbrainz", baseUrlProperties=["musicbrainz.url"], registerBean=True),
        ConfigureWireMock(
            name="setlist-fm",
            baseUrlProperties=["micronaut.http.services.setlist-fm.url"],
            registerBean=True,
        ),
    ]
)

context: Annotated[ApplicationContext, Inject]
music_brainz: Annotated[WireMockServer, Inject, Named("musicbrainz")]
setlist_fm: Annotated[WireMockServer, Inject, Named("setlist-fm")]

MBID = "a74b1b7f-71a5-4011-9441-d0b5e4122711"
ARTIST_PATH = f"/ws/2/artist/{MBID}"
SETLISTS_PATH = f"/rest/1.0/artist/{MBID}/setlists"


@BeforeEach
def stub_happy_path():
    music_brainz.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(ARTIST_PATH))
        .withQueryParam("fmt", WireMock.equalTo("json"))
        .willReturn(
            WireMock.okJson(
                f"""
                {{"id": "{MBID}", "name": "Radiohead", "sort-name": "Radiohead",
                  "type": "Group", "country": "GB"}}
                """
            )
        )
    )
    setlist_fm.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(SETLISTS_PATH)).willReturn(
            WireMock.okJson(
                """
                {"type": "setlists", "itemsPerPage": 20, "page": 1, "total": 1,
                 "setlist": [{"eventDate": "04-11-2025",
                              "venue": {"name": "Movistar Arena",
                                        "city": {"name": "Madrid"}}}]}
                """
            )
        )
    )


@Test
def test_combines_both_upstreams():
    assert artist() == {
        "name": "Radiohead",
        "country": "GB",
        "type": "Group",
        "recentSetlists": [{"eventDate": "04-11-2025", "venue": "Movistar Arena", "city": "Madrid"}],
    }


@Test
def test_sends_the_api_key_and_user_agent():
    artist()

    setlist_fm.verify(
        WireMock.getRequestedFor(WireMock.urlPathEqualTo(SETLISTS_PATH)).withHeader(
            "x-api-key", WireMock.equalTo("dummy-setlist-fm-api-key")
        )
    )
    music_brainz.verify(
        WireMock.getRequestedFor(WireMock.urlPathEqualTo(ARTIST_PATH)).withHeader(
            "User-Agent", WireMock.equalTo("wiremock-micronaut-example/1.0")
        )
    )


@Test
def test_unknown_artist_is_not_found():
    music_brainz.stubFor(WireMock.get(WireMock.urlPathEqualTo(ARTIST_PATH)).willReturn(WireMock.notFound()))

    assert status() == 404


@Test
def test_artist_without_setlists_has_none():
    setlist_fm.stubFor(WireMock.get(WireMock.urlPathEqualTo(SETLISTS_PATH)).willReturn(WireMock.notFound()))

    assert artist()["recentSetlists"] == []


@Test
def test_upstream_error_becomes_bad_gateway():
    setlist_fm.stubFor(WireMock.get(WireMock.urlPathEqualTo(SETLISTS_PATH)).willReturn(WireMock.serverError()))

    assert status() == 502


@Test
def test_connection_fault_becomes_bad_gateway():
    setlist_fm.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(SETLISTS_PATH)).willReturn(
            WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)
        )
    )

    assert status() == 502


@Test
def test_missing_country_is_null():
    music_brainz.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(ARTIST_PATH)).willReturn(
            WireMock.okJson('{"name": "Radiohead", "type": "Group"}')
        )
    )

    assert artist()["country"] is None


@Test
def test_page_without_setlists_is_empty():
    setlist_fm.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(SETLISTS_PATH)).willReturn(
            WireMock.okJson('{"type": "setlists", "total": 0}')
        )
    )

    assert artist()["recentSetlists"] == []


def artist():
    response = requests.with_context(context).get(f"/artists/{MBID}")
    assert response.status_code == 200, f"{response.status_code}: {response.text}"
    return response.json()


def status():
    return requests.with_context(context).get(f"/artists/{MBID}").status_code
