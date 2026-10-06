package com.leeturner.wiremock.micronaut.fixtures;

import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import org.junit.jupiter.api.Test;

@EnableWireMock
public class WrongTypeFixture {
  @InjectWireMock String server;

  @Test
  void test() {}
}
