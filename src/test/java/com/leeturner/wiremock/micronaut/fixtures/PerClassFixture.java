package com.leeturner.wiremock.micronaut.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import io.micronaut.context.annotation.Value;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableWireMock(@ConfigureWireMock(name = "users", baseUrlProperties = "users.url"))
public class PerClassFixture {
  @InjectWireMock("users")
  WireMockServer users;

  @Value("${users.url}")
  String usersUrl;

  @Test
  void fieldAndPropertyAgree() {
    assertThat(usersUrl).isEqualTo("http://localhost:" + users.port());
  }
}
