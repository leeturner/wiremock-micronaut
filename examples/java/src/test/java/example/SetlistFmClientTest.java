package example;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock(
    @ConfigureWireMock(
        name = "setlist-fm",
        baseUrlProperties = "micronaut.http.services.setlist-fm.url"))
class SetlistFmClientTest {

  @Inject SetlistFmClient client;

  @InjectWireMock("setlist-fm")
  WireMockServer setlistFm;

  @Test
  void serviceIdClientIsRedirectedToWireMock() {
    setlistFm.stubFor(
        get("/rest/1.0/artist/abc")
            .willReturn(ok("Radiohead").withHeader("Content-Type", "text/plain")));

    assertThat(client.artist("abc")).isEqualTo("Radiohead");
    setlistFm.verify(getRequestedFor(urlEqualTo("/rest/1.0/artist/abc")));
  }
}
