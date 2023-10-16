package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage

data class Mismatch(
  var isMatch: Boolean,
  var isValid: Boolean,
  var calculableSentenceEnvelope: CalculableSentenceEnvelope,
  var calculatedReleaseDates: CalculatedReleaseDates? = null,
  var messages: List<ValidationMessage> = emptyList(),
) {
  fun shouldRecordMismatch(): Boolean {
    return !isValid || !isMatch
  }
}
