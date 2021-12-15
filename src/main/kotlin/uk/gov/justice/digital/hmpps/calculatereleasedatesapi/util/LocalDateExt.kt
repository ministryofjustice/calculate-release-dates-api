package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util

import java.time.DayOfWeek
import java.time.LocalDate

fun LocalDate.isBeforeOrEqualTo(date: LocalDate): Boolean {
  return this.isBefore(date) || this == date
}

fun LocalDate.isAfterOrEqualTo(date: LocalDate): Boolean {
  return this.isAfter(date) || this == date
}

fun LocalDate.isWeekend(): Boolean {
  return this.dayOfWeek == DayOfWeek.SATURDAY || this.dayOfWeek == DayOfWeek.SUNDAY
}

fun LocalDate.isFirstDayOfMonth(): Boolean {
  return this.dayOfMonth == 0
}

fun LocalDate.plusDaysToEndOfMonth(): LocalDate {
  return this.plusDays((this.month.length(this.isLeapYear) - this.dayOfMonth).toLong())
}
