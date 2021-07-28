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
    assertEquals("{Years=1}", duration.toString())
    assertEquals("{Years=1, Days=1}", durationTwo.toString())
  }

  @Test
  fun append() {
    val duration = Duration()
    duration.append(1L, ChronoUnit.YEARS)
    duration.append(1L, ChronoUnit.YEARS)
    assertEquals("{Years=2}", duration.toString())
  }

  @Test
  fun append1year6months() {
    val duration = Duration()
    duration.append(1L, ChronoUnit.YEARS)
    duration.append(6L, ChronoUnit.MONTHS)
    assertEquals("{Years=1, Months=6}", duration.toString())
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
