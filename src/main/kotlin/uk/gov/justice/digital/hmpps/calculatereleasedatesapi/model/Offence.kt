package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
import java.util.Optional

data class Offence(
  val committedAt: LocalDate,
  var isScheduleFifteen: Boolean = false
)
