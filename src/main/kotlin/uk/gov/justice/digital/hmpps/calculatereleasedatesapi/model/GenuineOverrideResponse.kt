package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class GenuineOverrideResponse(
  val reason: String,
  val originalCalculationRequest: String,
  val savedCalculation: String,
  val isOverridden: Boolean,
)
