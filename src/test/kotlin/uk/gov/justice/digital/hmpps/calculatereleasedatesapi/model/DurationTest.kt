package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal class DurationTest {

  @Test
  fun testCopy() {
    val duration = Duration()
    duration.append(1L, ChronoUnit.YEARS)
    val durationTwo = duration.copy(duration)
    durationTwo.append(1L, ChronoUnit.DAYS)
    assertEquals("1 years", duration.toString())
    assertEquals("1 years 1 days", durationTwo.toString())
  }

  @Test
  fun append() {
    val duration = Duration()
    duration.append(1L, ChronoUnit.YEARS)
    duration.append(1L, ChronoUnit.YEARS)
    assertEquals("2 years", duration.toString())
  }

  @Test
  fun append1year6months() {
    val duration = Duration()
    duration.append(1L, ChronoUnit.YEARS)
    duration.append(6L, ChronoUnit.MONTHS)
    assertEquals("1 years 6 months", duration.toString())
  }

  @Test
  fun getLengthInDaysOneDay() {
    val duration = Duration()
    duration.append(1L, ChronoUnit.DAYS)
    val length: Int = duration.getLengthInDays(LocalDate.of(2020, 1, 1))
    assertEquals(1, length)
  }

  @Test
  fun getLengthInDaysOneYearEighteenMonths() {
    val duration = Duration()
    duration.append(1L, ChronoUnit.YEARS)
    duration.append(6L, ChronoUnit.MONTHS)
    val length: Int = duration.getLengthInDays(LocalDate.of(2018, 1, 1))
    assertEquals(546, length)
  }
}
