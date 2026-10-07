package example

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn

/** The clients block, so requests run off the event loop. */
@Controller("/artists")
@ExecuteOn(TaskExecutors.BLOCKING)
class ArtistController(private val artists: ArtistService) {

    @Get("/{mbid}")
    fun artist(mbid: String): Artist = artists.artist(mbid)
}
