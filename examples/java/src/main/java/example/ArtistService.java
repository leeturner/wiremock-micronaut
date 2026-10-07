package example;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Singleton
public class ArtistService {

  private final MusicBrainzClient musicBrainz;
  private final SetlistFmClient setlistFm;

  ArtistService(MusicBrainzClient musicBrainz, SetlistFmClient setlistFm) {
    this.musicBrainz = musicBrainz;
    this.setlistFm = setlistFm;
  }

  public Artist artist(String mbid) {
    MusicBrainzArtist artist =
        call(() -> musicBrainz.artist(mbid))
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Artist not found"));
    // setlist.fm answers 404 for an artist with no setlists.
    List<Artist.SetlistSummary> setlists =
        call(() -> setlistFm.setlists(mbid)).map(SetlistPage::setlist).orElse(List.of()).stream()
            .map(
                s ->
                    new Artist.SetlistSummary(
                        s.eventDate(), s.venue().name(), s.venue().city().name()))
            .toList();
    return new Artist(artist.name(), artist.country(), artist.type(), setlists);
  }

  /**
   * The clients already turn a 404 into an empty Optional; any other upstream failure becomes our
   * 502.
   */
  private static <T> Optional<T> call(Supplier<Optional<T>> upstream) {
    try {
      return upstream.get();
    } catch (HttpClientException e) {
      throw new HttpStatusException(HttpStatus.BAD_GATEWAY, "Upstream call failed");
    }
  }
}
