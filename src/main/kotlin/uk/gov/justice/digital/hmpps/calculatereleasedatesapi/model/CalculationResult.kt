package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import java.time.LocalDate
import java.time.Period

data class CalculationResult(
  val dates: Map<ReleaseDateType, LocalDate>,
  val breakdownByReleaseDateType: Map<ReleaseDateType, ReleaseDateCalculationBreakdown> = mapOf(),
  val otherDates: Map<ReleaseDateType, LocalDate> = mapOf(),
  val effectiveSentenceLength: Period,
  val ersedNotApplicableDueToDtoLaterThanCrd: Boolean = false,
  val historicalTusedSource: HistoricalTusedSource? = null,
  val sdsEarlyReleaseAllocatedTranche: TrancheName = TrancheName.TRANCHE_0,
  val sdsEarlyReleaseTranche: TrancheName = TrancheName.TRANCHE_0,
  val affectedBySds40: Boolean = false,
  val showSds40Hints: Boolean = true,
  val usedPreviouslyRecordedSLED: PreviouslyRecordedSLED? = null,
  val trancheAllocationByLegislationName: Map<LegislationName, TrancheName> = emptyMap(),
  val sentencesImpactingFinalReleaseDate: List<AbstractSentence> = emptyList(),
)
