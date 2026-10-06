package com.leeturner.wiremock.micronaut;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.app.EagerClient;
import com.leeturner.wiremock.micronaut.app.UsersGateway;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock({
  @ConfigureWireMock(
      name = "users",
      baseUrlProperties = "users.url",
      portProperties = "users.port"),
  @ConfigureWireMock(name = "eager", baseUrlProperties = "eager.url")
})
class MicronautPropertyBindingTest {

  @Inject ApplicationContext context;
  @Inject UsersGateway users;
  @Inject EagerClient eager;

  @InjectWireMock("users")
  WireMockServer usersServer;

  @InjectWireMock("eager")
  WireMockServer eagerServer;

  @Test
  void bindsBaseUrlAndPort() {
    assertThat(context.getRequiredProperty("users.url", String.class))
        .isEqualTo("http://localhost:" + usersServer.port());
    assertThat(context.getRequiredProperty("users.port", Integer.class))
        .isEqualTo(usersServer.port());
  }

  @Test
  void beansTalkToWireMock() {
    usersServer.stubFor(get("/users").willReturn(ok("alice")));
    assertThat(users.fetch("/users")).isEqualTo("alice");
  }

  @Test
  void eagerBeansSeeThePropertyAtStartup() {
    assertThat(eager.url()).isEqualTo("http://localhost:" + eagerServer.port());
  }

  @Test
  void sharedDefaultPropertiesAreNotBound() {
    // only "eager" leaves portProperties at its default, so that one is bound...
    assertThat(context.getProperty("wiremock.server.port", Integer.class))
        .contains(eagerServer.port());
    // ...while both leave the default HTTPS base URL, and neither has HTTPS anyway
    assertThat(context.getProperty("wiremock.server.httpsBaseUrl", String.class)).isEmpty();
  }
}
