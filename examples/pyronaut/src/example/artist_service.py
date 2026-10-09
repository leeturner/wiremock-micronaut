from jakarta.inject import Singleton
from micronaut.http import HttpStatus
from micronaut.http.client.exceptions import HttpClientException
from micronaut.http.exceptions import HttpStatusException

from example.artist import Artist, SetlistSummary
from example.music_brainz_client import MusicBrainzClient
from example.setlist_fm_client import SetlistFmClient


@Singleton
class ArtistService:
    def __init__(self, music_brainz: MusicBrainzClient, setlist_fm: SetlistFmClient):
        self.music_brainz = music_brainz
        self.setlist_fm = setlist_fm

    def artist(self, mbid: str) -> Artist:
        artist = _call(lambda: self.music_brainz.artist(mbid))
        if artist is None:
            raise HttpStatusException(HttpStatus.NOT_FOUND, "Artist not found")
        # setlist.fm answers 404 for an artist with no setlists.
        page = _call(lambda: self.setlist_fm.setlists(mbid))
        setlists = [
            SetlistSummary(s.eventDate, s.venue.name, s.venue.city.name)
            for s in (page.setlist if page is not None and page.setlist else [])
        ]
        return Artist(artist.name, artist.country, artist.type, setlists)


def _call(upstream):
    """The clients already turn a 404 into None; any other upstream failure becomes our 502."""
    try:
        return upstream()
    except HttpClientException:
        raise HttpStatusException(HttpStatus.BAD_GATEWAY, "Upstream call failed")
