from dataclasses import dataclass

from micronaut.serde.annotation import Serdeable


@Serdeable
@dataclass
class City:
    name: str | None = None


@Serdeable
@dataclass
class Venue:
    name: str | None = None
    city: City | None = None


@Serdeable
@dataclass
class Setlist:
    eventDate: str | None = None
    venue: Venue | None = None


@Serdeable
@dataclass
class SetlistPage:
    """The fields we use from a setlist.fm page of setlists."""

    setlist: list[Setlist] | None = None
