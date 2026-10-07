package example;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every stub is declared in the test. Neither server configures files, so both also load the
 * default directory (src/test/resources/wiremock); stubs added here win because WireMock prefers
 * the most recently added stub.
 */
@MicronautTest
@EnableWireMock({
  @ConfigureWireMock(name = "musicbrainz", baseUrlProperties = "musicbrainz.url"),
  @ConfigureWireMock(
      name = "setlist-fm",
      baseUrlProperties = "micronaut.http.services.setlist-fm.url")
})
class ProgrammaticStubsTest {

  static final String MBID = "a74b1b7f-71a5-4011-9441-d0b5e4122711";
  static final String ARTIST_PATH = "/ws/2/artist/" + MBID;
  static final String SETLISTS_PATH = "/rest/1.0/artist/" + MBID + "/setlists";

  @Inject
  @Client("/")
  HttpClient http;

  @InjectWireMock("musicbrainz")
  WireMockServer musicBrainz;

  @InjectWireMock("setlist-fm")
  WireMockServer setlistFm;

  @BeforeEach
  void stubHappyPath() {
    musicBrainz.stubFor(
        get(urlPathEqualTo(ARTIST_PATH))
            .withQueryParam("fmt", equalTo("json"))
            .willReturn(
                okJson(
                    """
                    {"id": "%s", "name": "Radiohead", "sort-name": "Radiohead",
                     "type": "Group", "country": "GB"}
                    """
                        .formatted(MBID))));
    setlistFm.stubFor(
        get(urlPathEqualTo(SETLISTS_PATH))
            .willReturn(
                okJson(
                    """
                    {"type": "setlists", "itemsPerPage": 20, "page": 1, "total": 1,
                     "setlist": [{"eventDate": "04-11-2025",
                                  "venue": {"name": "Movistar Arena",
                                            "city": {"name": "Madrid"}}}]}
                    """)));
  }

  @Test
  void combinesBothUpstreams() {
    assertThat(artist())
        .isEqualTo(
            new Artist(
                "Radiohead",
                "GB",
                "Group",
                List.of(new Artist.SetlistSummary("04-11-2025", "Movistar Arena", "Madrid"))));
  }

  @Test
  void sendsTheApiKeyAndUserAgent() {
    artist();

    setlistFm.verify(
        getRequestedFor(urlPathEqualTo(SETLISTS_PATH))
            .withHeader("x-api-key", equalTo("dummy-setlist-fm-api-key")));
    musicBrainz.verify(
        getRequestedFor(urlPathEqualTo(ARTIST_PATH))
            .withHeader("User-Agent", equalTo("wiremock-micronaut-example/1.0")));
  }

  @Test
  void unknownArtistIsNotFound() {
    musicBrainz.stubFor(get(urlPathEqualTo(ARTIST_PATH)).willReturn(notFound()));

    assertStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void artistWithoutSetlistsHasNone() {
    setlistFm.stubFor(get(urlPathEqualTo(SETLISTS_PATH)).willReturn(notFound()));

    assertThat(artist().recentSetlists()).isEmpty();
  }

  @Test
  void upstreamErrorBecomesBadGateway() {
    setlistFm.stubFor(get(urlPathEqualTo(SETLISTS_PATH)).willReturn(serverError()));

    assertStatus(HttpStatus.BAD_GATEWAY);
  }

  @Test
  void connectionFaultBecomesBadGateway() {
    setlistFm.stubFor(
        get(urlPathEqualTo(SETLISTS_PATH))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertStatus(HttpStatus.BAD_GATEWAY);
  }

  @Test
  void missingCountryIsNull() {
    musicBrainz.stubFor(
        get(urlPathEqualTo(ARTIST_PATH))
            .willReturn(okJson("{\"name\": \"Radiohead\", \"type\": \"Group\"}")));

    assertThat(artist().country()).isNull();
  }

  @Test
  void pageWithoutSetlistsIsEmpty() {
    setlistFm.stubFor(
        get(urlPathEqualTo(SETLISTS_PATH))
            .willReturn(okJson("{\"type\": \"setlists\", \"total\": 0}")));

    assertThat(artist().recentSetlists()).isEmpty();
  }

  private Artist artist() {
    return http.toBlocking().retrieve("/artists/" + MBID, Artist.class);
  }

  private void assertStatus(HttpStatus expected) {
    assertThatThrownBy(() -> http.toBlocking().retrieve("/artists/" + MBID))
        .isInstanceOfSatisfying(
            HttpClientResponseException.class,
            e -> assertThat(e.getStatus().getCode()).isEqualTo(expected.getCode()));
  }
}
