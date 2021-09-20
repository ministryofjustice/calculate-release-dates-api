package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
import java.util.Optional

data class Offence(
  val startedAt: LocalDate = LocalDate.now(),
  val endedAt: Optional<LocalDate> = Optional.empty(),
  var isScheduleFifteen: Boolean = false
)
