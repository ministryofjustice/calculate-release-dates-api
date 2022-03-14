package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isFirstDayOfMonth
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.plusDaysUntilEndOfMonth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS

/*
  This represents human readable durations, for example
  2 -> Year
  6 -> Month
  22 -> Days

  It is useful for representing periods, where the underlying number of days is different.

  e.g. 1 month from the 31 Jan 2021 is 28 days, whereas 1 month from the 28th Feb 2021
  is 31 days a nearly 10% difference.
 */

data class Duration(
  var durationElements: Map<ChronoUnit, Long> = mapOf()
) {

  // PSI 5.5 Converting a Sentence in to Days
  fun getLengthInDays(startDate: LocalDate): Int {
    return (DAYS.between(startDate, getEndDate(startDate)) + 1).toInt()
  }

  // PSI 5.5 Converting a Sentence in to Days
  fun getEndDate(startDate: LocalDate): LocalDate {
    val years = durationElements.getOrDefault(YEARS, 0L)
    val months = durationElements.getOrDefault(MONTHS, 0L)
    val weeks = durationElements.getOrDefault(WEEKS, 0L)
    val days = durationElements.getOrDefault(DAYS, 0L)

    /*
    Policy confirmed that the years work exactly the same one year = 12 calendar months
    So 1 year will always be 365 days or 366 if it involves a leap year */

    val isDateAdjustedForFirstDayOfMonth = startDate.isFirstDayOfMonth() && (months != 0L || years != 0L)

    var calculatedDate = startDate
    if (!isDateAdjustedForFirstDayOfMonth) {
      calculatedDate = calculatedDate.minusDays(1)
    }

    calculatedDate = calculatedDate.plusYears(years)

    calculatedDate = if (isDateAdjustedForFirstDayOfMonth) {
      calculatedDate.plusMonths(months - 1).plusDaysUntilEndOfMonth()
    } else {
      calculatedDate.plusMonths(months)
    }

    calculatedDate = calculatedDate.plusWeeks(weeks)
    calculatedDate = calculatedDate.plusDays(days)
    return calculatedDate
  }

  override fun toString(): String {
    return this.durationElements.entries.sortedByDescending { it.key.ordinal }
      .filter { it.value != 0L }
      .joinToString(separator = " ") { "${it.value} ${nameForUnit(it.key, it.value)}" }
  }

  private fun nameForUnit(unit: ChronoUnit, size: Long): String {
    return if (size == 1L) {
      unit.toString().lowercase().dropLast(1) // Remove 's'
    } else {
      unit.toString().lowercase()
    }
  }

  fun toPeriodString(sentencedAt: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    return "From\t:\t${sentencedAt.format(formatter)}\n" +
      "To\t:\t${getEndDate(sentencedAt).format(formatter)}"
  }

  fun appendAll(durationElements: Map<ChronoUnit, Long>): Duration {
    val allElements = durationElements.toMutableMap()
    this.durationElements.forEach {
      if (!allElements.containsKey(it.key)) {
        allElements[it.key] = it.value
      } else {
        allElements[it.key] = it.value + allElements[it.key]!!
      }
    }
    return Duration(allElements.toMap())
  }
}
