package example;

import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonSchema;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * Not an example: guards against the Micronaut platform BOM overriding WireMock's dependencies
 * (JSON schema matching broke under an unshaded WireMock).
 */
@EnableWireMock
class MicronautBomCompatibilityTest {

  @InjectWireMock WireMockServer wireMock;

  @Test
  void jsonSchemaMatchingWorksUnderTheMicronautBom() throws Exception {
    wireMock.stubFor(
        post("/schema")
            .withRequestBody(matchingJsonSchema("{\"type\":\"object\",\"required\":[\"name\"]}"))
            .willReturn(ok("valid")));
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(URI.create(wireMock.baseUrl() + "/schema"))
                  .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"lee\"}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.body()).isEqualTo("valid");
    }
  }
}
