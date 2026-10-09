from dataclasses import dataclass

from micronaut.serde.annotation import Serdeable


@Serdeable
@dataclass
class MusicBrainzArtist:
    """The fields we use from MusicBrainz's artist lookup."""

    name: str | None = None
    country: str | None = None
    type: str | None = None
