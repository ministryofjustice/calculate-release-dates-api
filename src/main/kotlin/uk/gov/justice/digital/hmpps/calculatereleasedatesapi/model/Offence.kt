package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class Offence(
  val committedAt: LocalDate,
  val isScheduleFifteen: Boolean = false,
  val isScheduleFifteenMaximumLife: Boolean = false,
  val isPcscSds: Boolean = false,
  val isPcscSec250: Boolean = false,
  val isPcscSdsPlus: Boolean = false
)
