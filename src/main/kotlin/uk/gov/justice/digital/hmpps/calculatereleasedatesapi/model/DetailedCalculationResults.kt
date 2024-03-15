package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType

data class DetailedCalculationResults(
  val context: CalculationContext,
  val dates: Map<ReleaseDateType, DetailedDate>,
  val approvedDates: Map<ReleaseDateType, DetailedDate>?,
  val calculationOriginalData: CalculationOriginalData,
  val calculationBreakdown: CalculationBreakdown?,
  val breakdownMissingReason: BreakdownMissingReason? = null,
)
