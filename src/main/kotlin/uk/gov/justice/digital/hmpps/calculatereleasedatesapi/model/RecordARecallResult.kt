package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage

data class RecordARecallResult(
  val validationMessages: List<ValidationMessage> = emptyList(),
  val calculatedReleaseDates: CalculatedReleaseDates? = null,
)
