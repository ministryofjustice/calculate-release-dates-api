package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDate
import java.time.Period

data class Offender(
  val reference: String,
  val dateOfBirth: LocalDate,
  val isActiveSexOffender: Boolean = false,
) {
  fun getAgeOnDate(date: LocalDate): Double {
    val betweenDates = Period.between(this.dateOfBirth, date)
    return betweenDates.years + (MULTIPLIER * betweenDates.months) + (MULTIPLIER * betweenDates.days)
  }

  @JsonIgnore
  val underEighteenAt: (date: LocalDate) -> Boolean = {
    getAgeOnDate(it) < 18
  }

  companion object {
    private const val MULTIPLIER = 0.0001
  }
}
