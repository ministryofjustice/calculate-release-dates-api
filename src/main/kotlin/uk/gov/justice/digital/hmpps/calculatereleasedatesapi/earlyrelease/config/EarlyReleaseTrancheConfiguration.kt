package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class EarlyReleaseTrancheConfiguration(
 val type: EarlyReleaseTrancheType,
  val date: LocalDate,
  val duration: Int? = null,
  val unit: ChronoUnit? = null
)
