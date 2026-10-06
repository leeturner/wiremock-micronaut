package example

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.leeturner.wiremock.micronaut.ConfigureWireMock
import com.leeturner.wiremock.micronaut.EnableWireMock
import com.leeturner.wiremock.micronaut.InjectWireMock
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MicronautTest
@EnableWireMock(ConfigureWireMock(name = "users", baseUrlProperties = ["users.url"]))
class UsersClientTest {

    @Inject
    lateinit var client: UsersClient

    @InjectWireMock("users")
    lateinit var users: WireMockServer

    @Test
    fun `fetches a user through the declarative client`() {
        users.stubFor(get("/users/1").willReturn(ok("alice").withHeader("Content-Type", "text/plain")))
        assertThat(client.user("1")).isEqualTo("alice")
    }

    @Test
    fun `parameter injection works from Kotlin`(@InjectWireMock("users") server: WireMockServer) {
        assertThat(server).isSameAs(users)
    }
}
