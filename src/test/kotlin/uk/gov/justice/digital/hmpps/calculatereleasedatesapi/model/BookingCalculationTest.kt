package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NPD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import java.time.LocalDate

internal class BookingCalculationTest {

  @Test
  fun testConstructor() {
    val date = LocalDate.of(2021, 1, 1)
    val bookingCalculationTest = BookingCalculation()
    bookingCalculationTest.dates[ARD] = date
    bookingCalculationTest.dates[CRD] = date
    bookingCalculationTest.dates[NPD] = date
    bookingCalculationTest.dates[PED] = date
    bookingCalculationTest.dates[TUSED] = date
    bookingCalculationTest.dates[SED] = date
    bookingCalculationTest.dates[SLED] = date
    bookingCalculationTest.dates[LED] = date

    assertEquals(date, bookingCalculationTest.dates[ARD])
    assertEquals(date, bookingCalculationTest.dates[CRD])
    assertEquals(date, bookingCalculationTest.dates[NPD])
    assertEquals(date, bookingCalculationTest.dates[PED])
    assertEquals(date, bookingCalculationTest.dates[TUSED])
    assertEquals(date, bookingCalculationTest.dates[SED])
    assertEquals(date, bookingCalculationTest.dates[SLED])
    assertEquals(date, bookingCalculationTest.dates[LED])
  }

  @Test
  fun testSetters() {
    val date = LocalDate.of(2021, 1, 1)
    val date2 = LocalDate.of(2021, 1, 2)
    val bookingCalculationTest = BookingCalculation()
    bookingCalculationTest.dates[ARD] = date
    bookingCalculationTest.dates[CRD] = date
    bookingCalculationTest.dates[NPD] = date
    bookingCalculationTest.dates[PED] = date
    bookingCalculationTest.dates[TUSED] = date
    bookingCalculationTest.dates[SED] = date
    bookingCalculationTest.dates[SLED] = date
    bookingCalculationTest.dates[LED] = date

    bookingCalculationTest.dates[ARD] = date2
    bookingCalculationTest.dates[CRD] = date2
    bookingCalculationTest.dates[NPD] = date2
    bookingCalculationTest.dates[PED] = date2
    bookingCalculationTest.dates[TUSED] = date2
    bookingCalculationTest.dates[SED] = date2
    bookingCalculationTest.dates[SLED] = date2
    bookingCalculationTest.dates[LED] = date2

    assertEquals(date2, bookingCalculationTest.dates[ARD])
    assertEquals(date2, bookingCalculationTest.dates[CRD])
    assertEquals(date2, bookingCalculationTest.dates[NPD])
    assertEquals(date2, bookingCalculationTest.dates[PED])
    assertEquals(date2, bookingCalculationTest.dates[TUSED])
    assertEquals(date2, bookingCalculationTest.dates[SED])
    assertEquals(date2, bookingCalculationTest.dates[SLED])
    assertEquals(date2, bookingCalculationTest.dates[LED])
  }
}
