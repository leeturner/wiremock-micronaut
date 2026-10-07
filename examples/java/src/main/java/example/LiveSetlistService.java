package example;

import io.micronaut.http.sse.Event;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

@Singleton
public class LiveSetlistService {

  private final LiveSetlistClient client;

  LiveSetlistService(LiveSetlistClient client) {
    this.client = client;
  }

  /** Song titles as they are played; completes when the gig ends. */
  public Flux<String> songs(String gigId) {
    return Flux.from(client.live(gigId))
        .takeWhile(event -> !"end".equals(event.getName()))
        .filter(event -> "song".equals(event.getName()))
        .map(Event::getData);
  }
}
