package com.leeturner.wiremock.micronaut.app;

import com.leeturner.wiremock.micronaut.testsupport.Http;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
public class UsersGateway {
  private final String baseUrl;

  public UsersGateway(@Value("${users.url}") String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String fetch(String path) {
    return Http.get(baseUrl + path).body();
  }
}
