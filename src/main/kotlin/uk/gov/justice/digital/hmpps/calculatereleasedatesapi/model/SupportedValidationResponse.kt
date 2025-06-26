package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage

data class SupportedValidationResponse(
  val unsupportedSentenceMessages: List<ValidationMessage> = listOf(),
  val unsupportedCalculationMessages: List<ValidationMessage> = listOf(),
  val unsupportedManualMessages: List<ValidationMessage> = listOf(),
)
