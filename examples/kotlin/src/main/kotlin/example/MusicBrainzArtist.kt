package example

import io.micronaut.serde.annotation.Serdeable

/** The fields we use from MusicBrainz's artist lookup. */
@Serdeable
data class MusicBrainzArtist(val name: String, val country: String?, val type: String?)
