package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util

import java.time.LocalDate

fun LocalDate.isBeforeOrEqualTo(date: LocalDate): Boolean {
  return this.isBefore(date) || this == date
}

fun LocalDate.isAfterOrEqualTo(date: LocalDate): Boolean {
  return this.isAfter(date) || this == date
}
