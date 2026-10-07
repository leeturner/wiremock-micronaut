package com.leeturner.wiremock.micronaut;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.message;
import static com.github.tomakehurst.wiremock.client.WireMock.sendSse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern;
import static com.github.tomakehurst.wiremock.message.MessagePattern.messagePattern;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.message.MessageStubMapping;
import com.leeturner.wiremock.micronaut.testsupport.Http;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@EnableWireMock(@ConfigureWireMock(filesUnderClasspath = "sse-stubs"))
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MessageStubsTest {

  @InjectWireMock WireMockServer server;

  @Test
  @Order(1)
  void fileMessageStubSendsAnSseEventWhenTriggered() throws Exception {
    server.messageStubFor(
        message()
            .withName("programmatic")
            .triggeredByHttpRequest(newRequestPattern().withUrl(urlPathEqualTo("/programmatic")))
            .willTriggerActions(
                sendSse("p")
                    .onChannelsMatching(
                        newRequestPattern().withUrl(urlPathEqualTo("/events")).build())));

    int channelsBefore = server.listAllMessageChannels().getChannels().size();
    try (Stream<String> lines = Http.stream(server.baseUrl() + "/events").body()) {
      awaitChannelCount(channelsBefore + 1);
      Http.get(server.baseUrl() + "/trigger");

      String data =
          CompletableFuture.supplyAsync(
                  () -> lines.filter(l -> l.startsWith("data:")).findFirst().orElseThrow())
              .get(5, SECONDS);
      assertThat(data.substring("data:".length()).strip()).isEqualTo("hello");
    }
    assertThat(
            server.waitForMessageEvent(
                messagePattern().withBody(equalTo("hello")).build(), Duration.ofSeconds(5)))
        .isPresent();
  }

  @Test
  @Order(2)
  void programmaticMessageStubsAndTheJournalAreResetButFileStubsStay() {
    assertThat(server.getMessageStubMappingsList())
        .extracting(MessageStubMapping::getName)
        .containsExactly("hello on trigger");
    assertThat(server.getAllMessageServeEvents()).isEmpty();
    // A disconnected SSE channel stays listed until a send to it fails, so waiting for
    // "any channel" would return at once here; wait for the count to rise instead.
    assertThat(server.listAllMessageChannels().getChannels()).isNotEmpty();
  }

  /** Events sent before the client connects have no channel and are dropped. */
  private void awaitChannelCount(int count) throws InterruptedException {
    long deadline = System.nanoTime() + SECONDS.toNanos(5);
    while (server.listAllMessageChannels().getChannels().size() < count) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("SSE channel never opened");
      }
      Thread.sleep(20);
    }
  }
}
