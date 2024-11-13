package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class CalculationOutput(
  val sentences: List<CalculableSentence>,
  val custodialPeriod: List<CustodialPeriod>,
  val calculationResult: CalculationResult,
)
