package com.leeturner.wiremock.micronaut;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@EnableWireMock({@ConfigureWireMock(name = "a"), @ConfigureWireMock(name = "b")})
class StaticDslWithSeveralServersTest {

  @BeforeAll
  static void pointStaticClientNowhere() {
    // Runs after the extension's beforeAll and before its beforeEach.
    WireMock.configureFor(-1);
  }

  @Test
  void staticClientIsNotPointedAtEitherServer() {
    // beforeEach must leave the static client alone when several servers exist.
    assertThatThrownBy(WireMock::listAllStubMappings).isInstanceOf(Exception.class);
  }
}
