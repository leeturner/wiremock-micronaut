package com.leeturner.wiremock.micronaut;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock(
    @ConfigureWireMock(
        name = "setlist-fm",
        baseUrlProperties = "micronaut.http.services.setlist-fm.url"))
class ServiceIdPropertyTest {

  @Inject ApplicationContext context;

  @InjectWireMock("setlist-fm")
  WireMockServer setlistFm;

  @Test
  void wireMockOverridesTheServiceUrlFromApplicationConfiguration() {
    assertThat(context.getRequiredProperty("micronaut.http.services.setlist-fm.url", String.class))
        .isEqualTo("http://localhost:" + setlistFm.port());
  }
}
