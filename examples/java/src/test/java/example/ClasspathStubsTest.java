package example;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Each server loads its own mappings from the classpath
 * (src/test/resources/stubs-by-server/<name>). Response bodies live in each folder's __files
 * directory and are referenced with bodyFileName.
 */
@MicronautTest
@EnableWireMock({
  @ConfigureWireMock(
      name = "musicbrainz",
      baseUrlProperties = "musicbrainz.url",
      filesUnderClasspath = "stubs-by-server/musicbrainz"),
  @ConfigureWireMock(
      name = "setlist-fm",
      baseUrlProperties = "micronaut.http.services.setlist-fm.url",
      filesUnderClasspath = "stubs-by-server/setlist-fm")
})
class ClasspathStubsTest {

  static final String MBID = "a74b1b7f-71a5-4011-9441-d0b5e4122711";

  @Inject
  @Client("/")
  HttpClient http;

  @InjectWireMock("setlist-fm")
  WireMockServer setlistFm;

  @Test
  void servesTheArtistFromMappingFiles() {
    Artist artist = http.toBlocking().retrieve("/artists/" + MBID, Artist.class);

    assertThat(artist)
        .isEqualTo(
            new Artist(
                "Radiohead",
                "GB",
                "Group",
                List.of(
                    new Artist.SetlistSummary("14-11-2025", "Unipol Arena", "Bologna"),
                    new Artist.SetlistSummary("04-11-2025", "Movistar Arena", "Madrid"))));
  }

  @Test
  void aStubInTheTestOverridesAFileStub() {
    setlistFm.stubFor(
        get(urlPathEqualTo("/rest/1.0/artist/" + MBID + "/setlists"))
            .atPriority(1)
            .willReturn(serverError()));

    assertThatThrownBy(() -> http.toBlocking().retrieve("/artists/" + MBID))
        .isInstanceOfSatisfying(
            HttpClientResponseException.class,
            e -> assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_GATEWAY.getCode()));
  }
}
