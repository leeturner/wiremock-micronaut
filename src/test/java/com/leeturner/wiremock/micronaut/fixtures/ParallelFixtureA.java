package com.leeturner.wiremock.micronaut.fixtures;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import com.leeturner.wiremock.micronaut.testsupport.Http;
import org.junit.jupiter.api.RepeatedTest;

@EnableWireMock(@ConfigureWireMock(name = "a"))
public class ParallelFixtureA {
  @InjectWireMock("a")
  WireMockServer server;

  @RepeatedTest(5)
  void servesItsOwnStub() {
    server.stubFor(get("/who").willReturn(ok("a")));
    assertThat(Http.get(server.baseUrl() + "/who").body()).isEqualTo("a");
  }
}
