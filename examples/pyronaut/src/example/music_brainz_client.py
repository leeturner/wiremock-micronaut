from abc import ABC, abstractmethod

from micronaut.http.annotation import Get, Header
from micronaut.http.client.annotation import Client

from example.music_brainz_artist import MusicBrainzArtist


@Client("${musicbrainz.url}")
@Header(name="User-Agent", value="wiremock-micronaut-example/1.0")
class MusicBrainzClient(ABC):
    """MusicBrainz asks every client to identify itself with a User-Agent."""

    @Get("/ws/2/artist/{mbid}?fmt=json")
    @abstractmethod
    def artist(self, mbid: str) -> MusicBrainzArtist | None:
        """None when MusicBrainz returns 404."""
