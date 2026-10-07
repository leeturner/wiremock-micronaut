package example

import io.micronaut.serde.annotation.Serdeable

/** Our response. */
@Serdeable
data class Artist(
    val name: String,
    val country: String?,
    val type: String?,
    val recentSetlists: List<SetlistSummary>,
)

@Serdeable
data class SetlistSummary(val eventDate: String, val venue: String, val city: String)
