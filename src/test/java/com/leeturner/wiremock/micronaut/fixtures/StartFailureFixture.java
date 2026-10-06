package com.leeturner.wiremock.micronaut.fixtures;

import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock({
  @ConfigureWireMock(name = "ok"),
  @ConfigureWireMock(name = "bad", port = -1, httpsPort = 0, keystorePath = "does-not-exist.jks")
})
public class StartFailureFixture {
  @Test
  void test() {}
}
