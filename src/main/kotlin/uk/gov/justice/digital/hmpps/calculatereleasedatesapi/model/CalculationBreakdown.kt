package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate

@Schema(description = "Calculation breakdown details")
data class CalculationBreakdown(
  val concurrentSentences: List<ConcurrentSentenceBreakdown>,
  val consecutiveSentence: ConsecutiveSentenceBreakdown?,
  @Schema(description = "Breakdown details in a map keyed by release date type")
  val breakdownByReleaseDateType: Map<ReleaseDateType, ReleaseDateCalculationBreakdown> = emptyMap(),
  val otherDates: Map<ReleaseDateType, LocalDate> = emptyMap(),
  var ersedNotApplicableDueToDtoLaterThanCrd: Boolean = false
)
