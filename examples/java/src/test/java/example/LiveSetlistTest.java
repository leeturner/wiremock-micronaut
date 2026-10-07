package example;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.message.MessagePattern.messagePattern;
import static java.util.concurrent.TimeUnit.SECONDS;
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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * The upstream streams a gig's setlist as server-sent events. mappings/live.json opens the SSE
 * channel and declares a "start" stub; message-mappings/live-setlist.json sends the songs when that
 * stub is hit.
 */
@MicronautTest
@EnableWireMock(
    @ConfigureWireMock(
        name = "live-setlist",
        baseUrlProperties = "micronaut.http.services.live-setlist.url",
        filesUnderClasspath = "stubs-by-server/live-setlist"))
class LiveSetlistTest {

  static final String GIG = "radiohead-2026";

  @Inject LiveSetlistService service;

  @InjectWireMock("live-setlist")
  WireMockServer liveSetlist;

  @Test
  void streamsSongsUntilTheGigEnds() throws Exception {
    CompletableFuture<List<String>> songs = service.songs(GIG).collectList().toFuture();

    awaitOpenChannel();
    HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create(liveSetlist.baseUrl() + "/gigs/" + GIG + "/start"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.discarding());

    assertThat(songs.get(5, SECONDS)).containsExactly("Airbag", "Paranoid Android");
    assertThat(
            liveSetlist.waitForMessageEvent(
                messagePattern().withBody(equalTo("encore over")).build(), Duration.ofSeconds(5)))
        .isPresent();
  }

  /** Trigger only once the app is connected: events sent before then have no channel. */
  private void awaitOpenChannel() throws InterruptedException {
    long deadline = System.nanoTime() + SECONDS.toNanos(5);
    while (liveSetlist.listAllMessageChannels().getChannels().isEmpty()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("The app never opened the SSE stream");
      }
      Thread.sleep(20);
    }
  }
}
