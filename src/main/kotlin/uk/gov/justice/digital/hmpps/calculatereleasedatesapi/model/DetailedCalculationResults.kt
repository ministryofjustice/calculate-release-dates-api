package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName

data class DetailedCalculationResults(
  val context: CalculationContext,
  val dates: Map<ReleaseDateType, DetailedDate>,
  val approvedDates: Map<ReleaseDateType, DetailedDate>?,
  val calculationOriginalData: CalculationOriginalData,
  val calculationBreakdown: CalculationBreakdown?,
  val breakdownMissingReason: BreakdownMissingReason? = null,
  val sds40Tranche: TrancheName? = null,
  val ftr56Tranche: TrancheName? = null,
  val progressionModelTranche: TrancheName? = null,
  val usedPreviouslyRecordedSLED: PreviouslyRecordedSLED? = null,
)
