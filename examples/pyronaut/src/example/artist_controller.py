from micronaut.http.annotation import Controller, Get
from micronaut.scheduling import TaskExecutors
from micronaut.scheduling.annotation import ExecuteOn

from example.artist import Artist
from example.artist_service import ArtistService


@Controller("/artists")
@ExecuteOn(TaskExecutors.BLOCKING)
class ArtistController:
    """The clients block, so requests run off the event loop."""

    def __init__(self, artists: ArtistService):
        self.artists = artists

    @Get("/{mbid}")
    def artist(self, mbid: str) -> Artist:
        return self.artists.artist(mbid)
