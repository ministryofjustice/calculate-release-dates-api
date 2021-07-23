package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal class DurationTest {

  @Test
  fun testCopyConstructor() {
    val duration = Duration(1.0, ChronoUnit.YEARS)
    val durationTwo = Duration(duration)
    durationTwo.append(1.0, ChronoUnit.DAYS)
    assertEquals("{Years=1.0}", duration.toString())
    assertEquals("{Years=1.0, Days=1.0}", durationTwo.toString())
  }

  @Test
  fun append() {
    val duration = Duration(1.0, ChronoUnit.YEARS)
    duration.append(1.0, ChronoUnit.YEARS)
    assertEquals("{Years=2.0}", duration.toString())
  }

  @Test
  fun append1year6months() {
    val duration = Duration(1.0, ChronoUnit.YEARS)
    duration.append(6.0, ChronoUnit.MONTHS)
    assertEquals("{Years=1.0, Months=6.0}", duration.toString())
  }

  @Test
  fun getLengthInDaysOneDay() {
    val duration = Duration(1.0, ChronoUnit.DAYS)
    val length: Int = duration.getLengthInDays(LocalDate.of(2020, 1, 1))
    assertEquals(1, length)
  }

  @Test
  fun getLengthInDaysOneYearEighteenMonths() {
    val duration = Duration(1.0, ChronoUnit.YEARS)
    duration.append(6.0, ChronoUnit.MONTHS)
    val length: Int = duration.getLengthInDays(LocalDate.of(2018, 1, 1))
    assertEquals(546, length)
  }
}
