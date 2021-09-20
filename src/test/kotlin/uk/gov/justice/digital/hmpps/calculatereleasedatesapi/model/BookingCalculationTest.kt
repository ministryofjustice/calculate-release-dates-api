package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import java.time.LocalDate

internal class BookingCalculationTest {

  @Test
  fun testConstructor() {
    val date = LocalDate.of(2021, 1, 1)
    val bookingCalculationTest = BookingCalculation()
    bookingCalculationTest.dates[SentenceType.ARD] = date
    bookingCalculationTest.dates[SentenceType.CRD] = date
    bookingCalculationTest.dates[SentenceType.NPD] = date
    bookingCalculationTest.dates[SentenceType.PED] = date
    bookingCalculationTest.dates[SentenceType.TUSED] = date
    bookingCalculationTest.dates[SentenceType.SED] = date
    bookingCalculationTest.dates[SentenceType.SLED] = date
    bookingCalculationTest.dates[SentenceType.LED] = date

    assertEquals(date, bookingCalculationTest.dates[SentenceType.ARD])
    assertEquals(date, bookingCalculationTest.dates[SentenceType.CRD])
    assertEquals(date, bookingCalculationTest.dates[SentenceType.NPD])
    assertEquals(date, bookingCalculationTest.dates[SentenceType.PED])
    assertEquals(date, bookingCalculationTest.dates[SentenceType.TUSED])
    assertEquals(date, bookingCalculationTest.dates[SentenceType.SED])
    assertEquals(date, bookingCalculationTest.dates[SentenceType.SLED])
    assertEquals(date, bookingCalculationTest.dates[SentenceType.LED])
  }

  @Test
  fun testSetters() {
    val date = LocalDate.of(2021, 1, 1)
    val date2 = LocalDate.of(2021, 1, 2)
    val bookingCalculationTest = BookingCalculation()
    bookingCalculationTest.dates[SentenceType.ARD] = date
    bookingCalculationTest.dates[SentenceType.CRD] = date
    bookingCalculationTest.dates[SentenceType.NPD] = date
    bookingCalculationTest.dates[SentenceType.PED] = date
    bookingCalculationTest.dates[SentenceType.TUSED] = date
    bookingCalculationTest.dates[SentenceType.SED] = date
    bookingCalculationTest.dates[SentenceType.SLED] = date
    bookingCalculationTest.dates[SentenceType.LED] = date

    bookingCalculationTest.dates[SentenceType.ARD] = date2
    bookingCalculationTest.dates[SentenceType.CRD] = date2
    bookingCalculationTest.dates[SentenceType.NPD] = date2
    bookingCalculationTest.dates[SentenceType.PED] = date2
    bookingCalculationTest.dates[SentenceType.TUSED] = date2
    bookingCalculationTest.dates[SentenceType.SED] = date2
    bookingCalculationTest.dates[SentenceType.SLED] = date2
    bookingCalculationTest.dates[SentenceType.LED] = date2

    assertEquals(date2, bookingCalculationTest.dates[SentenceType.ARD])
    assertEquals(date2, bookingCalculationTest.dates[SentenceType.CRD])
    assertEquals(date2, bookingCalculationTest.dates[SentenceType.NPD])
    assertEquals(date2, bookingCalculationTest.dates[SentenceType.PED])
    assertEquals(date2, bookingCalculationTest.dates[SentenceType.TUSED])
    assertEquals(date2, bookingCalculationTest.dates[SentenceType.SED])
    assertEquals(date2, bookingCalculationTest.dates[SentenceType.SLED])
    assertEquals(date2, bookingCalculationTest.dates[SentenceType.LED])
  }
}
