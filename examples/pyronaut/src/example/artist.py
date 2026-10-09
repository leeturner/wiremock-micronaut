from dataclasses import dataclass

from micronaut.serde.annotation import Serdeable


@Serdeable
@dataclass
class SetlistSummary:
    eventDate: str
    venue: str
    city: str


@Serdeable
@dataclass
class Artist:
    """Our response."""

    name: str
    country: str | None
    type: str | None
    recentSetlists: list[SetlistSummary]
