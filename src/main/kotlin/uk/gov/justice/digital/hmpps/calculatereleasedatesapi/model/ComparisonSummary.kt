package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import java.time.LocalDateTime

data class ComparisonSummary(
  val comparisonShortReference: String,
  val prison: String?,
  val comparisonType: ComparisonType,
  val comparisonStatus: ComparisonStatus,
  val calculatedAt: LocalDateTime,
  val calculatedByUsername: String,
  val numberOfMismatches: Long,
  val numberOfPeopleExpected: Long,
  val numberOfPeopleCompared: Long,
  val numberOfPeopleComparisonFailedFor: Long,
  val comparisonProgress: ComparisonProgress = ComparisonProgress.from(comparisonStatus, numberOfPeopleCompared, numberOfPeopleExpected, calculatedAt),
)
