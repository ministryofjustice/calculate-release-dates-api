package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate
import java.time.Period

data class Offender(
  val reference: String,
  val name: String,
  private val dateOfBirth: LocalDate,
) {
  fun getAgeOnDate(date: LocalDate): Double {
    val betweenDates = Period.between(this.dateOfBirth, date)
    return betweenDates.years + (MULTIPLIER * betweenDates.months) + (MULTIPLIER * betweenDates.days)
  }

  companion object {
    private const val MULTIPLIER = 0.0001
  }
}
