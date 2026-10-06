package com.leeturner.wiremock.micronaut.fixtures;

import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock({
  @ConfigureWireMock(name = "a", baseUrlProperties = "x.url"),
  @ConfigureWireMock(name = "b", baseUrlProperties = "x.url")
})
public class PropertyClashFixture {
  @Test
  void test() {}
}
