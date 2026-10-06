package com.leeturner.wiremock.micronaut.fixtures;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock(
    @ConfigureWireMock(name = "bean", registerBean = true, baseUrlProperties = "bean.url"))
public class NestedInjectParameterFixture {
  @Nested
  class Inner {
    @Test
    void test(@InjectWireMock("bean") WireMockServer server) {}
  }
}
