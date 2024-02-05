package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import java.time.LocalDateTime

data class ComparisonOverview(
  val comparisonShortReference: String,
  val prison: String?,
  val comparisonType: ComparisonType,
  val calculatedAt: LocalDateTime,
  val calculatedByUsername: String,
  val numberOfMismatches: Long,
  val numberOfPeopleCompared: Long,
  val mismatches: List<ComparisonMismatchSummary>,
  val status: String,
  val hdc4PlusCalculated: List<ComparisonMismatchSummary>,
)
