package com.leeturner.wiremock.micronaut.fixtures;

import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@EnableWireMock
public class NestedDeclarationFixture {
  @Nested
  @ConfigureWireMock(name = "x")
  class Inner {
    @Test
    void test() {}
  }
}
