package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import java.time.LocalDate
import java.time.Period

data class TestExpectedCalculationResult(
  val dates: Map<ReleaseDateType, LocalDate>,
  val effectiveSentenceLength: Period,
  val sdsEarlyReleaseAllocatedTranche: TrancheName? = null,
  val sdsEarlyReleaseTranche: TrancheName? = null,
  val affectedBySds40: Boolean? = null,
  val trancheAllocationByLegislationName: Map<LegislationName, TrancheName>? = null,
)
