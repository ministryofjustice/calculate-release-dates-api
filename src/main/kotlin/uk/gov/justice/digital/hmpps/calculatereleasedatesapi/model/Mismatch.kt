package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope

data class Mismatch(
  var isMismatch: Boolean,
  var isValid: Boolean,
  var calculableSentenceEnvelope: CalculableSentenceEnvelope,
  var calculatedReleaseDates: CalculatedReleaseDates? = null,
) {
  fun shouldRecordMismatch(): Boolean {
    return !isValid || !isMismatch
  }
}
