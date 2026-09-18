package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class SentenceSnapshotCalculationBreakdown(
  val snapshotDate: LocalDate,
  val breakdown: ReleaseDateCalculationBreakdown,
  val awardedDuringCustodyAtSnapshot: Long,
)
