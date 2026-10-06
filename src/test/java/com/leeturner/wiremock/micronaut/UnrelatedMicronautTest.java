package com.leeturner.wiremock.micronaut;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
class UnrelatedMicronautTest {
  @Inject ApplicationContext context;

  @Test
  void contextHasNoWireMockProperties() {
    assertThat(context.getProperty("wiremock.server.baseUrl", String.class)).isEmpty();
    // the app's real service URL is untouched when WireMock isn't in use
    assertThat(context.getRequiredProperty("micronaut.http.services.setlist-fm.url", String.class))
        .isEqualTo("https://api.setlist.fm");
  }
}
