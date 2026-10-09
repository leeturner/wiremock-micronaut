from abc import ABC, abstractmethod

from micronaut.http.annotation import Get, Header
from micronaut.http.client.annotation import Client

from example.setlist_page import SetlistPage


@Client("setlist-fm")
@Header(name="x-api-key", value="${setlist-fm.api-key}")
class SetlistFmClient(ABC):
    """setlist.fm authenticates with an x-api-key header."""

    @Get("/rest/1.0/artist/{mbid}/setlists")
    @abstractmethod
    def setlists(self, mbid: str) -> SetlistPage | None:
        """None when setlist.fm returns 404."""
