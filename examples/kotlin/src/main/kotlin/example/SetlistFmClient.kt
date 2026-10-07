package example

import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client

/** setlist.fm authenticates with an x-api-key header. */
@Client("setlist-fm")
@Header(name = "x-api-key", value = "\${setlist-fm.api-key}")
interface SetlistFmClient {

    /** Null when setlist.fm returns 404. */
    @Get("/rest/1.0/artist/{mbid}/setlists")
    fun setlists(mbid: String): SetlistPage?
}
