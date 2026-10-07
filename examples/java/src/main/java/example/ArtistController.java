package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

/** The clients block, so requests run off the event loop. */
@Controller("/artists")
@ExecuteOn(TaskExecutors.BLOCKING)
public class ArtistController {

  private final ArtistService artists;

  ArtistController(ArtistService artists) {
    this.artists = artists;
  }

  @Get("/{mbid}")
  public Artist artist(String mbid) {
    return artists.artist(mbid);
  }
}
