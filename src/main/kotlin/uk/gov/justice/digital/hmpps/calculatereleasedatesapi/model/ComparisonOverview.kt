package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDateTime

data class ComparisonOverview(
  val comparisonShortReference: String,
  val prison: String?,
  val calculatedAt: LocalDateTime,
  val calculatedByUsername: String,
  val numberOfMismatches: Long,
  val numberOfPeopleCompared: Long,
  val mismatches: List<ComparisonMismatchSummary>,
  val status: String,
)
