from jakarta.inject import Singleton
from reactor.core.publisher import Flux

from example.live_setlist_client import LiveSetlistClient


@Singleton
class LiveSetlistService:
    def __init__(self, client: LiveSetlistClient):
        self.client = client

    def songs(self, gigId: str) -> Flux[str]:
        """Song titles as they are played; completes when the gig ends."""
        return (
            Flux.from_(self.client.live(gigId))
            .takeWhile(lambda event: event.getName() != "end")
            .filter(lambda event: event.getName() == "song")
            .map(lambda event: event.getData())
        )
