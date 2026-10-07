package com.leeturner.wiremock.micronaut.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Stream;

public final class Http {
  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  private Http() {}

  public static HttpResponse<String> get(String url) {
    return send(
        HttpRequest.newBuilder(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  /** Returns once headers arrive; the body lines are read lazily (for SSE). */
  public static HttpResponse<Stream<String>> stream(String url) {
    return send(
        HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofLines());
  }

  public static HttpResponse<String> post(String url, String body) {
    return send(
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static <T> HttpResponse<T> send(
      HttpRequest request, HttpResponse.BodyHandler<T> handler) {
    try {
      return CLIENT.send(request, handler);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
