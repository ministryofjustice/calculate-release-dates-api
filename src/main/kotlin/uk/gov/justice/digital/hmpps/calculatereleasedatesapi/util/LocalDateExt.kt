package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util

import java.time.DayOfWeek
import java.time.LocalDate

fun LocalDate.isBeforeOrEqualTo(date: LocalDate): Boolean = this.isBefore(date) || this == date

fun LocalDate.isAfterOrEqualTo(date: LocalDate): Boolean = this.isAfter(date) || this == date

fun LocalDate.isWeekend(): Boolean = this.dayOfWeek == DayOfWeek.SATURDAY || this.dayOfWeek == DayOfWeek.SUNDAY

fun LocalDate.isNonWorkingDay(bankHolidays: List<LocalDate>): Boolean = isWeekend() || bankHolidays.contains(this)

fun LocalDate.isFirstDayOfMonth(): Boolean = this.dayOfMonth == 1

/*
  Adds days to the date until it reaches the last day of the month.
 */
fun LocalDate.plusDaysUntilEndOfMonth(): LocalDate = this.plusDays((this.month.length(this.isLeapYear) - this.dayOfMonth).toLong())
