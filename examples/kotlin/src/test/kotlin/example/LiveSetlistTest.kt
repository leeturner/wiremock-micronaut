package example

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.message.MessagePattern.messagePattern
import com.leeturner.wiremock.micronaut.ConfigureWireMock
import com.leeturner.wiremock.micronaut.EnableWireMock
import com.leeturner.wiremock.micronaut.InjectWireMock
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit.SECONDS

/**
 * The upstream streams a gig's setlist as server-sent events. mappings/live.json opens the SSE
 * channel and declares a "start" stub; message-mappings/live-setlist.json sends the songs when
 * that stub is hit.
 */
@MicronautTest
@EnableWireMock(
    ConfigureWireMock(
        name = "live-setlist",
        baseUrlProperties = ["micronaut.http.services.live-setlist.url"],
        filesUnderClasspath = "stubs-by-server/live-setlist",
    ),
)
class LiveSetlistTest {

    private val gig = "radiohead-2026"

    @Inject
    lateinit var service: LiveSetlistService

    @InjectWireMock("live-setlist")
    lateinit var liveSetlist: WireMockServer

    @Test
    fun `streams songs until the gig ends`() {
        val songs = service.songs(gig).collectList().toFuture()

        awaitOpenChannel()
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("${liveSetlist.baseUrl()}/gigs/$gig/start"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.discarding(),
        )

        assertThat(songs.get(5, SECONDS)).containsExactly("Airbag", "Paranoid Android")
        assertThat(
            liveSetlist.waitForMessageEvent(
                messagePattern().withBody(equalTo("encore over")).build(),
                Duration.ofSeconds(5),
            ),
        ).isPresent
    }

    /** Trigger only once the app is connected: events sent before then have no channel. */
    private fun awaitOpenChannel() {
        val deadline = System.nanoTime() + SECONDS.toNanos(5)
        while (liveSetlist.listAllMessageChannels().channels.isEmpty()) {
            if (System.nanoTime() > deadline) throw AssertionError("The app never opened the SSE stream")
            Thread.sleep(20)
        }
    }
}
