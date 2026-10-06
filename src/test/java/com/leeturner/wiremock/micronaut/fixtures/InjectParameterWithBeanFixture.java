package com.leeturner.wiremock.micronaut.fixtures;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock({
  @ConfigureWireMock(name = "plain", baseUrlProperties = "plain.url"),
  @ConfigureWireMock(name = "bean", registerBean = true, baseUrlProperties = "bean.url")
})
public class InjectParameterWithBeanFixture {
  @Test
  void test(@InjectWireMock("plain") WireMockServer plain) {}
}
