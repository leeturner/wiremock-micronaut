"""Each server loads its own mappings from the classpath
(tests-config/stubs-by-server/<name>). Response bodies live in each folder's
__files directory and are referenced with bodyFileName.
"""

from typing import Annotated

from com.github.tomakehurst.wiremock import WireMockServer
from com.github.tomakehurst.wiremock.client import WireMock
from com.leeturner.wiremock.micronaut import ConfigureWireMock, EnableWireMock
from jakarta.inject import Inject, Named
from micronaut.context import ApplicationContext
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from pyronaut import requests

MicronautTest()
EnableWireMock(
    [
        ConfigureWireMock(
            name="musicbrainz",
            baseUrlProperties=["musicbrainz.url"],
            filesUnderClasspath="stubs-by-server/musicbrainz",
        ),
        ConfigureWireMock(
            name="setlist-fm",
            baseUrlProperties=["micronaut.http.services.setlist-fm.url"],
            filesUnderClasspath="stubs-by-server/setlist-fm",
            registerBean=True,
        ),
    ]
)

context: Annotated[ApplicationContext, Inject]
setlist_fm: Annotated[WireMockServer, Inject, Named("setlist-fm")]

MBID = "a74b1b7f-71a5-4011-9441-d0b5e4122711"


@Test
def test_serves_the_artist_from_mapping_files():
    response = requests.with_context(context).get(f"/artists/{MBID}")

    assert response.json() == {
        "name": "Radiohead",
        "country": "GB",
        "type": "Group",
        "recentSetlists": [
            {"eventDate": "14-11-2025", "venue": "Unipol Arena", "city": "Bologna"},
            {"eventDate": "04-11-2025", "venue": "Movistar Arena", "city": "Madrid"},
        ],
    }


@Test
def test_a_stub_in_the_test_overrides_a_file_stub():
    setlist_fm.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(f"/rest/1.0/artist/{MBID}/setlists"))
        .atPriority(1)
        .willReturn(WireMock.serverError())
    )

    assert requests.with_context(context).get(f"/artists/{MBID}").status_code == 502
