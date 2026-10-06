package com.leeturner.wiremock.micronaut.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class Http {
  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  private Http() {}

  public static HttpResponse<String> get(String url) {
    return send(HttpRequest.newBuilder(URI.create(url)).GET().build());
  }

  public static HttpResponse<String> post(String url, String body) {
    return send(
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build());
  }

  private static HttpResponse<String> send(HttpRequest request) {
    try {
      return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
