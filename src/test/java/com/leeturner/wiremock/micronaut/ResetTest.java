package com.leeturner.wiremock.micronaut;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@EnableWireMock({
  @ConfigureWireMock(name = "reset"),
  @ConfigureWireMock(name = "keep", resetWireMockServer = false)
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResetTest {

  @InjectWireMock("reset")
  WireMockServer reset;

  @InjectWireMock("keep")
  WireMockServer keep;

  @Test
  @Order(1)
  void stubBoth() {
    reset.stubFor(get("/a").willReturn(ok()));
    keep.stubFor(get("/a").willReturn(ok()));
  }

  @Test
  @Order(2)
  void onlyTheResettingServerWasCleared() {
    assertThat(reset.getStubMappings()).isEmpty();
    assertThat(keep.getStubMappings()).hasSize(1);
  }
}
