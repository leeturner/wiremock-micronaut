"""setlist-fm configures no files, so it loads the first default directory that
exists: wiremock/. Every server without files configuration shares that
directory, so musicbrainz points at its own classpath folder instead.
"""

from typing import Annotated

from com.leeturner.wiremock.micronaut import ConfigureWireMock, EnableWireMock
from jakarta.inject import Inject
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
        ConfigureWireMock(name="setlist-fm", baseUrlProperties=["micronaut.http.services.setlist-fm.url"]),
    ]
)

context: Annotated[ApplicationContext, Inject]

MBID = "a74b1b7f-71a5-4011-9441-d0b5e4122711"


@Test
def test_setlists_come_from_the_default_directory():
    artist = requests.with_context(context).get(f"/artists/{MBID}").json()

    assert artist["name"] == "Radiohead"
    assert artist["recentSetlists"] == [
        {"eventDate": "21-11-2025", "venue": "The O2 Arena", "city": "London"}
    ]
