package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class GenuineOverrideCreatedResponse(
  val newCalculationRequestId: Long,
  val originalCalculationRequestId: Long,
)
