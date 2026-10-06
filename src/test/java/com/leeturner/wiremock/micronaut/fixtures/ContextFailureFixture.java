package com.leeturner.wiremock.micronaut.fixtures;

import com.leeturner.wiremock.micronaut.EnableWireMock;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "boom.enabled", value = "true")
@EnableWireMock
public class ContextFailureFixture {
  @Test
  void test() {}
}
