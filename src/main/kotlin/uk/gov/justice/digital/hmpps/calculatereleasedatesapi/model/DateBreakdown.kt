package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class DateBreakdown(
  val unadjusted: LocalDate,
  val adjusted: LocalDate,
  val daysBetween: Long
) {
  constructor(
    unadjusted: LocalDate,
    adjusted: LocalDate
  ) :
    this(unadjusted, adjusted, ChronoUnit.DAYS.between(adjusted, unadjusted))
}
