package example;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/** Our response. */
@Serdeable
public record Artist(
    String name, String country, String type, List<SetlistSummary> recentSetlists) {

  @Serdeable
  public record SetlistSummary(String eventDate, String venue, String city) {}
}
