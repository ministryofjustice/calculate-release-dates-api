package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTrancheCategory
import java.time.LocalDate
import java.time.Period

data class CalculationResult(
  val dates: Map<ReleaseDateType, LocalDate>,
  val breakdownByReleaseDateType: Map<ReleaseDateType, ReleaseDateCalculationBreakdown> = mapOf(),
  val otherDates: Map<ReleaseDateType, LocalDate> = mapOf(),
  val effectiveSentenceLength: Period,
  val ersedNotApplicableDueToDtoLaterThanCrd: Boolean = false,
  val historicalTusedSource: HistoricalTusedSource? = null,
  val sdsEarlyReleaseAllocatedTranche: SDSEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_0,
  val sdsEarlyReleaseTranche: SDSEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_0,
  val affectedBySds40: Boolean = false,
  val showSds40Hints: Boolean = true,
  val usedPreviouslyRecordedSLED: PreviouslyRecordedSLED? = null,
  val trancheAllocationByCategory: Map<SDSEarlyReleaseTrancheCategory, SDSEarlyReleaseTranche> = emptyMap(),
)
