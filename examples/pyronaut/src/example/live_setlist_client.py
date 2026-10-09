from abc import ABC, abstractmethod

from micronaut.http import MediaType
from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client
from micronaut.http.sse import Event
from org.reactivestreams import Publisher


@Client("live-setlist")
class LiveSetlistClient(ABC):
    """Streams a gig's setlist as server-sent events: "song" per song, then "end"."""

    @Get(value="/gigs/{gigId}/live", processes=MediaType.TEXT_EVENT_STREAM)
    @abstractmethod
    def live(self, gigId: str) -> Publisher[Event[str]]: ...
