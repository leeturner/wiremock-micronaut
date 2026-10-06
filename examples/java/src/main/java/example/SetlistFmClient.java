package example;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

@Client("setlist-fm")
public interface SetlistFmClient {
  @Get(value = "/rest/1.0/artist/{mbid}", consumes = MediaType.TEXT_PLAIN)
  String artist(String mbid);
}
