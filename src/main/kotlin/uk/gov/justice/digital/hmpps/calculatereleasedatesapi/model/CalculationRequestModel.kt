package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class CalculationRequestModel(
  val calculationUserInputs: CalculationUserInputs?,
  val calculationReasonId: Long,
  val otherReasonDescription: String? = null,
)
