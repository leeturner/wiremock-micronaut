package com.leeturner.wiremock.micronaut;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.app.EagerWireMockHolder;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock(
    @ConfigureWireMock(name = "users", registerBean = true, baseUrlProperties = "users.url"))
class RegisterBeanTest {

  @Inject
  @Named("users")
  WireMockServer bean;

  @Inject EagerWireMockHolder eager;

  @InjectWireMock("users")
  WireMockServer injected;

  @Test
  void beanIsTheRunningServer() {
    assertThat(bean).isSameAs(injected);
    assertThat(bean.isRunning()).isTrue();
  }

  @Test
  void eagerSingletonsReceiveTheServer() {
    assertThat(eager.server()).isSameAs(injected);
  }

  @Test
  void injectWireMockParameterDoesNotCompeteWithMicronaut(
      @Named("users") WireMockServer parameter) {
    assertThat(parameter).isSameAs(injected);
  }
}
