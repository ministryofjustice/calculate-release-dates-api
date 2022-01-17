package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class Adjustment(
  val fromDate: LocalDate,
  val toDate: LocalDate? = null,
  val numberOfDays: Int
)
