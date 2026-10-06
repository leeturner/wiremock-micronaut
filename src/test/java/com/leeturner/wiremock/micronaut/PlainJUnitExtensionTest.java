package com.leeturner.wiremock.micronaut;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit.Stubbing;
import com.leeturner.wiremock.micronaut.testsupport.Http;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@EnableWireMock(@ConfigureWireMock(name = "users"))
class PlainJUnitExtensionTest {

  @InjectWireMock("users")
  WireMockServer users;

  @InjectWireMock("users")
  Stubbing usersAsStubbing;

  @Test
  void injectsFieldsAndParameters(@InjectWireMock("users") WireMockServer parameter) {
    assertThat(users.isRunning()).isTrue();
    assertThat(parameter).isSameAs(users);
    assertThat(usersAsStubbing).isSameAs(users);
  }

  @Test
  void staticDslPointsAtTheOnlyServer() {
    stubFor(get("/hello").willReturn(ok("hi")));
    assertThat(Http.get(users.baseUrl() + "/hello").body()).isEqualTo("hi");
  }

  @Nested
  class Inner {
    @InjectWireMock("users")
    WireMockServer inner;

    @Test
    void nestedTestsShareTheEnclosingServer() {
      assertThat(inner).isSameAs(users);
    }
  }
}
