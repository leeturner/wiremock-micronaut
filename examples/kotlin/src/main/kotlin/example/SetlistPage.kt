package example

import io.micronaut.serde.annotation.Serdeable

/** The fields we use from a setlist.fm page of setlists. */
@Serdeable
data class SetlistPage(val setlist: List<Setlist>?)

@Serdeable
data class Setlist(val eventDate: String, val venue: Venue)

@Serdeable
data class Venue(val name: String, val city: City)

@Serdeable
data class City(val name: String)
