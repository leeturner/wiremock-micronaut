package example;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
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

  /** Empty on an upstream 404; any other upstream failure becomes our 502. */
  private static <T> Optional<T> call(Supplier<Optional<T>> upstream) {
    try {
      return upstream.get();
    } catch (HttpClientResponseException e) {
      if (e.getStatus() == HttpStatus.NOT_FOUND) {
        return Optional.empty();
      }
      throw badGateway();
    } catch (HttpClientException e) {
      throw badGateway();
    }
  }

  private static HttpStatusException badGateway() {
    return new HttpStatusException(HttpStatus.BAD_GATEWAY, "Upstream call failed");
  }
}
