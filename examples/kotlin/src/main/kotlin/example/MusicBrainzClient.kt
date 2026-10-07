package example

import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client

/** MusicBrainz asks every client to identify itself with a User-Agent. */
@Client("\${musicbrainz.url}")
@Header(name = "User-Agent", value = "wiremock-micronaut-example/1.0")
interface MusicBrainzClient {

    /** Null when MusicBrainz returns 404. */
    @Get("/ws/2/artist/{mbid}?fmt=json")
    fun artist(mbid: String): MusicBrainzArtist?
}
