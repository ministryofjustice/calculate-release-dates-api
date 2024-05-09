package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class ReleaseDatesAndCalculationContext(
  val calculation: CalculationContext,
  val dates: List<DetailedDate>,
)
