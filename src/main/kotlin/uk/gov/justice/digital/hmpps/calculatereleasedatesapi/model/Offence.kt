package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class Offence(
  val committedAt: LocalDate,
  var isScheduleFifteen: Boolean = false,
  var isScheduleFifteenMaximumLife: Boolean = false
)
