package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class TrancheConfiguration(
  val type: TrancheType,
  val date: LocalDate,
  val duration: Int? = null,
  val unit: ChronoUnit? = null,
  val name: TrancheName,
)
