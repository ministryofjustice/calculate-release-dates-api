package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDateTime

data class NomisCalculationSummary(
  val reason: String,
  val calculatedAt: LocalDateTime,
  val comment: String? = null,
  val releaseDates: List<DetailedDate>,
  val calculatedByUsername: String,
  val calculatedByDisplayName: String,
)
