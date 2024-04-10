package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack

data class ReleaseDateMultiplier(val tracks: List<SentenceIdentificationTrack>, val multiplier: Double)
