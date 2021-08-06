package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class BookingCalculationTest {

  @Test
  fun testConstructor() {
    val date = LocalDate.of(2021, 1, 1)
    val bookingCalculationTest = BookingCalculation(
      date,
      date,
      date,
      date,
      true
    )
    assertEquals(true, bookingCalculationTest.isReleaseDateConditional)
    assertEquals(date, bookingCalculationTest.releaseDate)
    assertEquals(date, bookingCalculationTest.licenceExpiryDate)
    assertEquals(date, bookingCalculationTest.sentenceExpiryDate)
    assertEquals(date, bookingCalculationTest.topUpSupervisionDate)
  }

  @Test
  fun testSetters() {
    val date = LocalDate.of(2021, 1, 1)
    val date2 = LocalDate.of(2021, 1, 2)
    val bookingCalculationTest = BookingCalculation(
      date,
      date,
      date,
      date,
      true
    )
    bookingCalculationTest.isReleaseDateConditional = false
    bookingCalculationTest.releaseDate = date2
    bookingCalculationTest.licenceExpiryDate = date2
    bookingCalculationTest.sentenceExpiryDate = date2
    bookingCalculationTest.topUpSupervisionDate = date2

    assertEquals(false, bookingCalculationTest.isReleaseDateConditional)
    assertEquals(date2, bookingCalculationTest.releaseDate)
    assertEquals(date2, bookingCalculationTest.licenceExpiryDate)
    assertEquals(date2, bookingCalculationTest.sentenceExpiryDate)
    assertEquals(date2, bookingCalculationTest.topUpSupervisionDate)
  }
}
