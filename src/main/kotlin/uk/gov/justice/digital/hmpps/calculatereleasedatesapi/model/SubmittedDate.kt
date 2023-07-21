package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class SubmittedDate(
  val day: Int,
  val month: Int,
  val year: Int,
) {
  fun toLocalDate(): LocalDate {
    return LocalDate.of(year, month, day)
  }
}
