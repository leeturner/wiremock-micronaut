package example;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonSchema;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock(@ConfigureWireMock(name = "users", baseUrlProperties = "users.url"))
class UsersClientTest {

  @Inject UsersClient client;

  @InjectWireMock("users")
  WireMockServer users;

  @Test
  void fetchesAUserThroughTheDeclarativeClient() {
    users.stubFor(get("/users/1").willReturn(ok("alice").withHeader("Content-Type", "text/plain")));
    assertThat(client.user("1")).isEqualTo("alice");
  }

  @Test
  void jsonSchemaMatchingWorksUnderTheMicronautBom() throws Exception {
    users.stubFor(
        post("/schema")
            .withRequestBody(matchingJsonSchema("{\"type\":\"object\",\"required\":[\"name\"]}"))
            .willReturn(ok("valid")));
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(users.baseUrl() + "/schema"))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"lee\"}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(response.body()).isEqualTo("valid");
  }
}
