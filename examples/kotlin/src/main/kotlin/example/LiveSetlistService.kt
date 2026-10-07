package example

import jakarta.inject.Singleton
import reactor.core.publisher.Flux

@Singleton
class LiveSetlistService(private val client: LiveSetlistClient) {

    /** Song titles as they are played; completes when the gig ends. */
    fun songs(gigId: String): Flux<String> =
        Flux.from(client.live(gigId))
            .takeWhile { it.name != "end" }
            .filter { it.name == "song" }
            .map { it.data }
}
