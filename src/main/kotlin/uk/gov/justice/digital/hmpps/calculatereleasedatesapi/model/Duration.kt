package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
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

class Duration() {

  private var durationElements: MutableMap<ChronoUnit, Double> = mutableMapOf<ChronoUnit, Double>()

  constructor(length: Double, period: ChronoUnit) : this() {
    durationElements[period] = length
  }
  constructor(that: Duration) : this() {
    for (duration in that.durationElements) {
      durationElements[duration.key] = duration.value
    }
  }

  fun append(length: Double, period: ChronoUnit) {
    if (!this.durationElements.containsKey(period)) {
      this.durationElements[period] = length
    } else {
      this.durationElements[period] = length + this.durationElements[period]!!
    }
  }

  fun getLengthInDays(startDate: LocalDate): Int {
    return (DAYS.between(startDate, getEndDate(startDate)) + 1).toInt()
  }

  private fun getEndDate(startDate: LocalDate): LocalDate {
    var calculatedDate = startDate
    for (duration in this.durationElements) {
      calculatedDate = calculatedDate.plus(duration.value.toLong(), duration.key)
    }
    return calculatedDate.minusDays(1L)
  }

  override fun toString(): String {
    return this.durationElements.toString()
  }
}
