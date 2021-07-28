package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class OffenderSentenceProfileCalculationTest {

  @Test
  fun testConstructor() {
    val date = LocalDate.of(2021, 1, 1)
    val offenderSentenceProfileCalculationTest = OffenderSentenceProfileCalculation(
      date,
      date,
      date,
      date,
      true
    )
    assertEquals(true, offenderSentenceProfileCalculationTest.isReleaseDateConditional)
    assertEquals(date, offenderSentenceProfileCalculationTest.releaseDate)
    assertEquals(date, offenderSentenceProfileCalculationTest.licenceExpiryDate)
    assertEquals(date, offenderSentenceProfileCalculationTest.sentenceExpiryDate)
    assertEquals(date, offenderSentenceProfileCalculationTest.topUpSupervisionDate)
  }

  @Test
  fun testSetters() {
    val date = LocalDate.of(2021, 1, 1)
    val date2 = LocalDate.of(2021, 1, 2)
    val offenderSentenceProfileCalculationTest = OffenderSentenceProfileCalculation(
      date,
      date,
      date,
      date,
      true
    )
    offenderSentenceProfileCalculationTest.isReleaseDateConditional = false
    offenderSentenceProfileCalculationTest.releaseDate = date2
    offenderSentenceProfileCalculationTest.licenceExpiryDate = date2
    offenderSentenceProfileCalculationTest.sentenceExpiryDate = date2
    offenderSentenceProfileCalculationTest.topUpSupervisionDate = date2

    assertEquals(false, offenderSentenceProfileCalculationTest.isReleaseDateConditional)
    assertEquals(date2, offenderSentenceProfileCalculationTest.releaseDate)
    assertEquals(date2, offenderSentenceProfileCalculationTest.licenceExpiryDate)
    assertEquals(date2, offenderSentenceProfileCalculationTest.sentenceExpiryDate)
    assertEquals(date2, offenderSentenceProfileCalculationTest.topUpSupervisionDate)
  }
}
