package com.leeturner.wiremock.micronaut;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@MicronautTest(rebuildContext = true)
@EnableWireMock(@ConfigureWireMock(name = "users", baseUrlProperties = "users.url"))
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RebuildContextTest {
  static final List<String> SEEN = new CopyOnWriteArrayList<>();

  @Inject ApplicationContext context;

  @BeforeAll
  static void forgetPreviousRuns() {
    SEEN.clear();
  }

  @Test
  @Order(1)
  void first() {
    SEEN.add(context.getRequiredProperty("users.url", String.class));
  }

  @Test
  @Order(2)
  void rebuiltContextKeepsTheSameServer() {
    SEEN.add(context.getRequiredProperty("users.url", String.class));
    assertThat(SEEN).hasSize(2);
    assertThat(SEEN.get(1)).isEqualTo(SEEN.get(0));
  }
}
