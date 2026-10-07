package example

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.sse.Event
import org.reactivestreams.Publisher

/** Streams a gig's setlist as server-sent events: "song" per song, then "end". */
@Client("live-setlist")
interface LiveSetlistClient {

    @Get(value = "/gigs/{gigId}/live", processes = [MediaType.TEXT_EVENT_STREAM])
    fun live(gigId: String): Publisher<Event<String>>
}
