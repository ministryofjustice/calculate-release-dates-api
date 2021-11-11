package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.DAYS

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
  var durationElements: MutableMap<ChronoUnit, Long> = mutableMapOf()
) {

  fun append(length: Long, period: ChronoUnit) {
    if (!this.durationElements.containsKey(period)) {
      this.durationElements[period] = length
    } else {
      this.durationElements[period] = length + this.durationElements[period]!!
    }
  }

  fun getLengthInDays(startDate: LocalDate): Int {
    return (DAYS.between(startDate, getEndDate(startDate)) + 1).toInt()
  }

  fun getEndDate(startDate: LocalDate): LocalDate {
    var calculatedDate = startDate.minusDays(1L)
    for (duration in this.durationElements) {
      calculatedDate = calculatedDate.plus(duration.value, duration.key)
    }
    return calculatedDate
  }

  fun copy(durationToCopy: Duration): Duration {
    val durationOutcome = Duration()
    durationOutcome.durationElements.putAll(durationToCopy.durationElements)
    return durationOutcome
  }

  override fun toString(): String {
    var durations = ""
    for ((key, value) in this.durationElements) {
      if (value != 0L) {
        durations += "$value ${nameForUnit(key, value)} "
      }
    }
    return durations.trim()
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

  fun appendAll(durationElements: MutableMap<ChronoUnit, Long>): Duration {
    durationElements.forEach { append(it.value, it.key) }
    return this
  }
}
