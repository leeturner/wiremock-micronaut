package example;

import io.micronaut.serde.annotation.Serdeable;

/** The fields we use from MusicBrainz's artist lookup. */
@Serdeable
public record MusicBrainzArtist(String name, String country, String type) {}
