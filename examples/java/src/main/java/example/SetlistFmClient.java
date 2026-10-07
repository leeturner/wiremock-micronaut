package example;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.annotation.Client;
import java.util.Optional;

/** setlist.fm authenticates with an x-api-key header. */
@Client("setlist-fm")
@Header(name = "x-api-key", value = "${setlist-fm.api-key}")
public interface SetlistFmClient {

  /** Empty when setlist.fm returns 404. */
  @Get("/rest/1.0/artist/{mbid}/setlists")
  Optional<SetlistPage> setlists(String mbid);
}
