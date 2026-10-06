package com.leeturner.wiremock.micronaut.fixtures;

import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import org.junit.jupiter.api.Test;

@EnableWireMock(@ConfigureWireMock(name = "files", filesUnderDirectory = "does-not-exist"))
public class MissingDirectoryFixture {
  @Test
  void test() {}
}
