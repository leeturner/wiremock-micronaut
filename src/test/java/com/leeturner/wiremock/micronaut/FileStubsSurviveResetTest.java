package com.leeturner.wiremock.micronaut;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.testsupport.Http;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@EnableWireMock(@ConfigureWireMock(filesUnderClasspath = "classpath-stubs"))
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileStubsSurviveResetTest {

  @InjectWireMock WireMockServer server;

  @Test
  @Order(1)
  void addAProgrammaticStub() {
    server.stubFor(get("/programmatic").willReturn(ok("p")));
    assertThat(Http.get(server.baseUrl() + "/from-classpath").body()).isEqualTo("classpath");
  }

  @Test
  @Order(2)
  void fileStubSurvivesButProgrammaticStubIsGone() {
    assertThat(Http.get(server.baseUrl() + "/from-classpath").body()).isEqualTo("classpath");
    assertThat(Http.get(server.baseUrl() + "/programmatic").statusCode()).isEqualTo(404);
  }

  @Test
  @Order(3)
  void bodyFileNameServesTheBodyFromFilesDirectory() {
    assertThat(Http.get(server.baseUrl() + "/from-body-file").body().strip())
        .isEqualTo("from __files");
  }
}
