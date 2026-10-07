package example

import com.leeturner.wiremock.micronaut.ConfigureWireMock
import com.leeturner.wiremock.micronaut.EnableWireMock
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * setlist-fm configures no files, so it loads the first default directory that exists:
 * src/test/resources/wiremock. Every server without files configuration shares that directory,
 * so musicbrainz points at its own classpath folder instead.
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
    ),
)
class DefaultDirectoryTest {

    @Inject
    @field:Client("/")
    lateinit var http: HttpClient

    @Test
    fun `setlists come from the default directory`() {
        val artist = http.toBlocking().retrieve("/artists/a74b1b7f-71a5-4011-9441-d0b5e4122711", Artist::class.java)

        assertThat(artist.name).isEqualTo("Radiohead")
        assertThat(artist.recentSetlists).containsExactly(SetlistSummary("21-11-2025", "The O2 Arena", "London"))
    }
}
