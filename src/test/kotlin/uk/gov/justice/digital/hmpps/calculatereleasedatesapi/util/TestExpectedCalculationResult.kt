package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import java.time.LocalDate
import java.time.Period

data class TestExpectedCalculationResult(
  val dates: Map<ReleaseDateType, LocalDate>? = null,
  val effectiveSentenceLength: Period? = null,
  val sdsEarlyReleaseAllocatedTranche: TrancheName? = null,
  val sdsEarlyReleaseTranche: TrancheName? = null,
  val affectedBySds40: Boolean? = null,
  val trancheAllocationByLegislationName: Map<LegislationName, TrancheName>? = null,
  val skipAssertingDates: Boolean? = null,
) {
  init {
    require(dates != null || skipAssertingDates == true) { "dates are missing but you have not skipped asserting dates" }
    require(effectiveSentenceLength != null || skipAssertingDates == true) { "effectiveSentenceLength is missing but you have not skipped asserting dates" }
  }
}
