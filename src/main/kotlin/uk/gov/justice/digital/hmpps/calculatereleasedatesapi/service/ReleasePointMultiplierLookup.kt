package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ReleasePointMultipliersConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack

@Service
class ReleasePointMultiplierLookup(val configuration: ReleasePointMultipliersConfiguration) {

  fun multiplierFor(track: SentenceIdentificationTrack): Double {
    return configuration.multipliers.find { track in it.tracks }?.multiplier ?: configuration.default
  }
}
