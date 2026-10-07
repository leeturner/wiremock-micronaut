package example

import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.exceptions.HttpStatusException
import jakarta.inject.Singleton

@Singleton
class ArtistService(
    private val musicBrainz: MusicBrainzClient,
    private val setlistFm: SetlistFmClient,
) {

    fun artist(mbid: String): Artist {
        val artist = call { musicBrainz.artist(mbid) }
            ?: throw HttpStatusException(HttpStatus.NOT_FOUND, "Artist not found")
        // setlist.fm answers 404 for an artist with no setlists.
        val setlists = call { setlistFm.setlists(mbid) }?.setlist.orEmpty()
        return Artist(
            name = artist.name,
            country = artist.country,
            type = artist.type,
            recentSetlists = setlists.map { SetlistSummary(it.eventDate, it.venue.name, it.venue.city.name) },
        )
    }

    /** Null on an upstream 404; any other upstream failure becomes our 502. */
    private fun <T : Any> call(upstream: () -> T?): T? =
        try {
            upstream()
        } catch (e: HttpClientResponseException) {
            if (e.status == HttpStatus.NOT_FOUND) null else throw badGateway()
        } catch (e: HttpClientException) {
            throw badGateway()
        }

    private fun badGateway() = HttpStatusException(HttpStatus.BAD_GATEWAY, "Upstream call failed")
}
