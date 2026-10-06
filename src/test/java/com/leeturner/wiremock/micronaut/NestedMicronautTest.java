package com.leeturner.wiremock.micronaut;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.testsupport.Http;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock(@ConfigureWireMock(name = "users", baseUrlProperties = "users.url"))
class NestedMicronautTest {
  private static final AtomicInteger OUTER_PORT = new AtomicInteger();

  @Inject ApplicationContext context;

  @InjectWireMock("users")
  WireMockServer users;

  @BeforeAll
  static void forgetPort() {
    OUTER_PORT.set(0);
  }

  @Test
  void outerTestRecordsThePort() {
    OUTER_PORT.set(users.port());
    assertThat(users.port()).isPositive();
  }

  @Nested
  class Inner {
    @Inject ApplicationContext nestedContext;

    @InjectWireMock("users")
    WireMockServer nestedUsers;

    @Test
    void seesTheBoundProperty() {
      assertThat(nestedContext.getRequiredProperty("users.url", String.class))
          .isEqualTo("http://localhost:" + nestedUsers.port());
    }

    @Test
    void sharesTheOuterServerInstance() {
      assertThat(nestedUsers).isSameAs(users);
    }

    @Test
    void servesStubsMadeInTheNestedTest() {
      nestedUsers.stubFor(get("/nested").willReturn(ok("nested")));
      String url = nestedContext.getRequiredProperty("users.url", String.class);
      assertThat(Http.get(url + "/nested").body()).isEqualTo("nested");
    }

    @Test
    void outerServerWasNotRestarted() {
      // The outer test (ordered by JUnit before or after) records the port of the same server.
      outerTestRecordsThePort();
      assertThat(nestedUsers.isRunning()).isTrue();
      assertThat(nestedUsers.port()).isEqualTo(OUTER_PORT.get());
      assertThat(nestedContext.getRequiredProperty("users.url", String.class))
          .isEqualTo("http://localhost:" + OUTER_PORT.get());
    }
  }
}
