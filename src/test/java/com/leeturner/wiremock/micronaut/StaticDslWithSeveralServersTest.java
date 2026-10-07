package com.leeturner.wiremock.micronaut;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@EnableWireMock({@ConfigureWireMock(name = "a"), @ConfigureWireMock(name = "b")})
class StaticDslWithSeveralServersTest {

  @BeforeAll
  static void pointStaticClientNowhere() throws IOException {
    // Runs after the extension's beforeAll and before its beforeEach.
    int unusedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      unusedPort = socket.getLocalPort();
    }
    WireMock.configureFor("localhost", unusedPort);
  }

  @Test
  void staticClientIsNotPointedAtEitherServer() {
    // beforeEach must leave the static client alone when several servers exist.
    assertThatThrownBy(WireMock::listAllStubMappings).isInstanceOf(ConnectException.class);
  }
}
