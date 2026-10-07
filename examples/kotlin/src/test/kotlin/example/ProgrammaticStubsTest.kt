package example

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.notFound
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.http.Fault
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val MBID = "a74b1b7f-71a5-4011-9441-d0b5e4122711"
private const val ARTIST_PATH = "/ws/2/artist/$MBID"
private const val SETLISTS_PATH = "/rest/1.0/artist/$MBID/setlists"

/**
 * Every stub is declared in the test. Neither server configures files, so both also load the
 * default directory (src/test/resources/wiremock); stubs added here win because WireMock prefers
 * the most recently added stub.
 */
@MicronautTest
@EnableWireMock(
    ConfigureWireMock(name = "musicbrainz", baseUrlProperties = ["musicbrainz.url"]),
    ConfigureWireMock(
        name = "setlist-fm",
        baseUrlProperties = ["micronaut.http.services.setlist-fm.url"],
    ),
)
class ProgrammaticStubsTest {

    @Inject
    @field:Client("/")
    lateinit var http: HttpClient

    @InjectWireMock("musicbrainz")
    lateinit var musicBrainz: WireMockServer

    @InjectWireMock("setlist-fm")
    lateinit var setlistFm: WireMockServer

    @BeforeEach
    fun `stub the happy path`() {
        musicBrainz.stubFor(
            get(urlPathEqualTo(ARTIST_PATH))
                .withQueryParam("fmt", equalTo("json"))
                .willReturn(
                    okJson(
                        """
                        {"id": "$MBID", "name": "Radiohead", "sort-name": "Radiohead",
                         "type": "Group", "country": "GB"}
                        """,
                    ),
                ),
        )
        setlistFm.stubFor(
            get(urlPathEqualTo(SETLISTS_PATH))
                .willReturn(
                    okJson(
                        """
                        {"type": "setlists", "itemsPerPage": 20, "page": 1, "total": 1,
                         "setlist": [{"eventDate": "04-11-2025",
                                      "venue": {"name": "Movistar Arena",
                                                "city": {"name": "Madrid"}}}]}
                        """,
                    ),
                ),
        )
    }

    @Test
    fun `combines both upstreams`() {
        assertThat(artist()).isEqualTo(
            Artist(
                name = "Radiohead",
                country = "GB",
                type = "Group",
                recentSetlists = listOf(SetlistSummary("04-11-2025", "Movistar Arena", "Madrid")),
            ),
        )
    }

    // The server is injected as a parameter here to show that parameter injection works too.
    @Test
    fun `sends the api key and user agent`(@InjectWireMock("setlist-fm") setlistFm: WireMockServer) {
        artist()

        setlistFm.verify(
            getRequestedFor(urlPathEqualTo(SETLISTS_PATH))
                .withHeader("x-api-key", equalTo("dummy-setlist-fm-api-key")),
        )
        musicBrainz.verify(
            getRequestedFor(urlPathEqualTo(ARTIST_PATH))
                .withHeader("User-Agent", equalTo("wiremock-micronaut-example/1.0")),
        )
    }

    @Test
    fun `unknown artist is not found`() {
        musicBrainz.stubFor(get(urlPathEqualTo(ARTIST_PATH)).willReturn(notFound()))

        assertStatus(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `artist without setlists has none`() {
        setlistFm.stubFor(get(urlPathEqualTo(SETLISTS_PATH)).willReturn(notFound()))

        assertThat(artist().recentSetlists).isEmpty()
    }

    @Test
    fun `upstream error becomes bad gateway`() {
        setlistFm.stubFor(get(urlPathEqualTo(SETLISTS_PATH)).willReturn(serverError()))

        assertStatus(HttpStatus.BAD_GATEWAY)
    }

    @Test
    fun `connection fault becomes bad gateway`() {
        setlistFm.stubFor(
            get(urlPathEqualTo(SETLISTS_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
        )

        assertStatus(HttpStatus.BAD_GATEWAY)
    }

    @Test
    fun `missing country is null`() {
        musicBrainz.stubFor(
            get(urlPathEqualTo(ARTIST_PATH)).willReturn(okJson("""{"name": "Radiohead", "type": "Group"}""")),
        )

        assertThat(artist().country).isNull()
    }

    @Test
    fun `page without setlists is empty`() {
        setlistFm.stubFor(
            get(urlPathEqualTo(SETLISTS_PATH)).willReturn(okJson("""{"type": "setlists", "total": 0}""")),
        )

        assertThat(artist().recentSetlists).isEmpty()
    }

    private fun artist(): Artist = http.toBlocking().retrieve("/artists/$MBID", Artist::class.java)

    private fun assertStatus(expected: HttpStatus) {
        assertThatThrownBy { http.toBlocking().retrieve("/artists/$MBID") }
            .isInstanceOfSatisfying(HttpClientResponseException::class.java) {
                assertThat(it.status.code).isEqualTo(expected.code)
            }
    }
}
