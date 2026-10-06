package com.leeturner.wiremock.micronaut.fixtures;

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import org.junit.jupiter.api.Test;

@EnableWireMock(
    @ConfigureWireMock(name = "ext", extensions = NoNoArgConstructorFixture.NeedsArgs.class))
public class NoNoArgConstructorFixture {
  public static class NeedsArgs implements ResponseTransformerV2 {
    public NeedsArgs(String unused) {}

    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
      return response;
    }

    @Override
    public String getName() {
      return "needs-args";
    }
  }

  @Test
  void test() {}
}
