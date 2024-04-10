package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack

interface MultiplierBySentenceIdentificationTrack {
  fun multiplierFor(track: SentenceIdentificationTrack): Double
}
