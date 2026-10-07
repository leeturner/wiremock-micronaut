package example;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/** The fields we use from a setlist.fm page of setlists. */
@Serdeable
public record SetlistPage(List<Setlist> setlist) {

  @Serdeable
  public record Setlist(String eventDate, Venue venue) {}

  @Serdeable
  public record Venue(String name, City city) {}

  @Serdeable
  public record City(String name) {}
}
