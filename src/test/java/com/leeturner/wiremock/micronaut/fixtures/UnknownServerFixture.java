package com.leeturner.wiremock.micronaut.fixtures;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import org.junit.jupiter.api.Test;

@EnableWireMock
public class UnknownServerFixture {
  @InjectWireMock("nope")
  WireMockServer server;

  @Test
  void test() {}
}
