package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class DateBreakdown(
  val unadjusted: LocalDate,
  val adjusted: LocalDate,
  val daysFromSentenceStart: Long,
  val adjustedByDays: Long,

) {
  constructor(
    unadjusted: LocalDate,
    adjusted: LocalDate,
    daysFromSentenceStart: Long,
  ) :
    this(unadjusted, adjusted, daysFromSentenceStart, ChronoUnit.DAYS.between(adjusted, unadjusted))
}
