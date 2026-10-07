package example

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.leeturner.wiremock.micronaut.ConfigureWireMock
import com.leeturner.wiremock.micronaut.EnableWireMock
import com.leeturner.wiremock.micronaut.InjectWireMock
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Each server loads its own mappings from the classpath (src/test/resources/stubs-by-server/<name>).
 * Response bodies live in each folder's __files directory and are referenced with bodyFileName.
 */
@MicronautTest
@EnableWireMock(
    ConfigureWireMock(
        name = "musicbrainz",
        baseUrlProperties = ["musicbrainz.url"],
        filesUnderClasspath = "stubs-by-server/musicbrainz",
    ),
    ConfigureWireMock(
        name = "setlist-fm",
        baseUrlProperties = ["micronaut.http.services.setlist-fm.url"],
        filesUnderClasspath = "stubs-by-server/setlist-fm",
    ),
)
class ClasspathStubsTest {

    private val mbid = "a74b1b7f-71a5-4011-9441-d0b5e4122711"

    @Inject
    @field:Client("/")
    lateinit var http: HttpClient

    @InjectWireMock("setlist-fm")
    lateinit var setlistFm: WireMockServer

    @Test
    fun `serves the artist from mapping files`() {
        val artist = http.toBlocking().retrieve("/artists/$mbid", Artist::class.java)

        assertThat(artist).isEqualTo(
            Artist(
                name = "Radiohead",
                country = "GB",
                type = "Group",
                recentSetlists = listOf(
                    SetlistSummary("14-11-2025", "Unipol Arena", "Bologna"),
                    SetlistSummary("04-11-2025", "Movistar Arena", "Madrid"),
                ),
            ),
        )
    }

    @Test
    fun `a stub in the test overrides a file stub`() {
        setlistFm.stubFor(
            get(urlPathEqualTo("/rest/1.0/artist/$mbid/setlists")).atPriority(1).willReturn(serverError()),
        )

        assertThatThrownBy { http.toBlocking().retrieve("/artists/$mbid") }
            .isInstanceOfSatisfying(HttpClientResponseException::class.java) {
                assertThat(it.status.code).isEqualTo(HttpStatus.BAD_GATEWAY.code)
            }
    }
}
