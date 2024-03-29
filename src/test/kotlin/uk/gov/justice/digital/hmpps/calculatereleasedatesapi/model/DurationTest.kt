package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal class DurationTest {

  @Test
  fun append1year6months() {
    val duration = Duration(
      mapOf(
        ChronoUnit.YEARS to 1L,
        ChronoUnit.MONTHS to 6L,
      ),
    )
    assertEquals("1 year 6 months", duration.toString())
  }

  @Test
  fun getLengthInDaysOneDay() {
    val duration = Duration(
      mapOf(
        ChronoUnit.DAYS to 1L,
      ),
    )
    val length: Int = duration.getLengthInDays(LocalDate.of(2020, 1, 1))
    assertEquals(1, length)
  }

  // This example is from PSI 5.5.3
  @Test
  fun `For example 3 months starting on 1 January (in a non leap year) runs to 31 March = 90 days`() {
    val duration = Duration(
      mapOf(
        ChronoUnit.MONTHS to 3L,
      ),
    )
    val sentenceAt = LocalDate.of(NOT_LEAP_YEAR, 1, 1)
    assertEquals(LocalDate.of(NOT_LEAP_YEAR, 3, 31), duration.getEndDate(sentenceAt))
    assertEquals(90, duration.getLengthInDays(sentenceAt))
  }

  // These examples are from PSI 5.5.4
  @Test
  fun `getEndDate() - 3 months starting 30 November runs to 28 February = 91 days`() {
    val duration = Duration(
      mapOf(
        ChronoUnit.MONTHS to 3L,
      ),
    )
    val sentenceAt = LocalDate.of(NOT_LEAP_YEAR - 1, 11, 30)
    assertEquals(LocalDate.of(NOT_LEAP_YEAR, 2, 28), duration.getEndDate(sentenceAt))
    assertEquals(91, duration.getLengthInDays(sentenceAt))
  }

  @Test
  fun `getEndDate() - 3 months starting 30 November runs to (29 February where February is in the leap year = 92 days)`() {
    val duration = Duration(
      mapOf(
        ChronoUnit.MONTHS to 3L,
      ),
    )
    val sentenceAt = LocalDate.of(LEAP_YEAR - 1, 11, 30)
    assertEquals(LocalDate.of(LEAP_YEAR, 2, 29), duration.getEndDate(sentenceAt))
    assertEquals(92, duration.getLengthInDays(sentenceAt))
  }

  @Test
  fun `getEndDate() - 3 months starting 29 November also runs to 28 February = 92 days`() {
    val duration = Duration(
      mapOf(
        ChronoUnit.MONTHS to 3L,
      ),
    )
    val sentenceAt = LocalDate.of(NOT_LEAP_YEAR - 1, 11, 29)
    assertEquals(LocalDate.of(NOT_LEAP_YEAR, 2, 28), duration.getEndDate(sentenceAt))
    assertEquals(92, duration.getLengthInDays(sentenceAt))
  }

  @Test
  fun `getEndDate() -  2 months starting 31 December runs to 28 February = 60 days`() {
    val duration = Duration(
      mapOf(
        ChronoUnit.MONTHS to 2L,
      ),
    )
    val sentenceAt = LocalDate.of(NOT_LEAP_YEAR - 1, 12, 31)
    assertEquals(LocalDate.of(NOT_LEAP_YEAR, 2, 28), duration.getEndDate(sentenceAt))
    assertEquals(60, duration.getLengthInDays(sentenceAt))
  }

  @Test
  fun `getEndDate() -  2 months starting 31 December runs to 29 February in a leap year = 61 days`() {
    val duration = Duration(
      mapOf(
        ChronoUnit.MONTHS to 2L,
      ),
    )
    val sentenceAt = LocalDate.of(LEAP_YEAR - 1, 12, 31)
    assertEquals(LocalDate.of(LEAP_YEAR, 2, 29), duration.getEndDate(sentenceAt))
    assertEquals(61, duration.getLengthInDays(sentenceAt))
  }

  @Test
  fun `getEndDate() - 3 months imposed on 28 February will run to 27 May = 89 days`() {
    val duration = Duration(
      mapOf(
        ChronoUnit.MONTHS to 3L,
      ),
    )
    val sentenceAt = LocalDate.of(NOT_LEAP_YEAR, 2, 28)
    assertEquals(LocalDate.of(NOT_LEAP_YEAR, 5, 27), duration.getEndDate(sentenceAt))
    assertEquals(89, duration.getLengthInDays(sentenceAt))
  }

  @Test
  fun `getEndDate() -  3 months imposed on 28 February will run to 27 May = 90 days in a leap year`() {
    val duration = Duration(
      mapOf(
        ChronoUnit.MONTHS to 3L,
      ),
    )
    val sentenceAt = LocalDate.of(LEAP_YEAR, 2, 28)
    assertEquals(LocalDate.of(LEAP_YEAR, 5, 27), duration.getEndDate(sentenceAt))
    assertEquals(90, duration.getLengthInDays(sentenceAt))
  }

  // This is not from the PSI.
  @Test
  fun `getEndDate() -  1 year imposed on 29 February will run to 28 Feb = 365 days`() {
    val duration = Duration(
      mapOf(
        ChronoUnit.YEARS to 1L,
      ),
    )
    val sentenceAt = LocalDate.of(LEAP_YEAR, 2, 29)
    assertEquals(LocalDate.of(LEAP_YEAR + 1, 2, 28), duration.getEndDate(sentenceAt))
    assertEquals(366, duration.getLengthInDays(sentenceAt))
  }

  @Test
  fun getLengthInDaysOneYearEighteenMonths() {
    val duration = Duration(
      mapOf(
        ChronoUnit.YEARS to 1L,
        ChronoUnit.MONTHS to 6L,
      ),
    )
    val length: Int = duration.getLengthInDays(LocalDate.of(2018, 1, 1))
    assertEquals(546, length)
  }

  @Test
  fun toStringWithZeros() {
    val duration = Duration(
      mapOf(
        ChronoUnit.YEARS to 1L,
        ChronoUnit.MONTHS to 0L,
        ChronoUnit.DAYS to 0L,
      ),
    )
    assertEquals("1 year", duration.toString())
  }

  companion object {
    const val LEAP_YEAR: Int = 2020
    const val NOT_LEAP_YEAR: Int = 2021
  }
}
