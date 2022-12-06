package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage

data class CalculationResults(
  val calculatedReleaseDates: CalculatedReleaseDates? = null,
  val validationMessages: List<ValidationMessage> = emptyList()
)
