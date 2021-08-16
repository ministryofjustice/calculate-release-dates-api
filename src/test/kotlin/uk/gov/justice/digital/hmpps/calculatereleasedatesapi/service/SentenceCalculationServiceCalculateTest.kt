package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import java.time.LocalDate

class SentenceCalculationServiceCalculateTest {

  private val sentenceCalculationService: SentenceCalculationService = SentenceCalculationService()
  private val sentenceIdentificationService: SentenceIdentificationService = SentenceIdentificationService()
  private val jsonTransformation = JsonTransformation()
  private val offender = jsonTransformation.loadOffender("john_doe")

  @Test
  fun `Example 9`() {
    val sentence = jsonTransformation.loadSentence("2_year_sep_2013")
    sentenceIdentificationService.identify(sentence, offender)
    val offender = Offender("A1234BC", "John Doe", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(sentence), mutableMapOf())
    val calculation = sentenceCalculationService.calculate(sentence, booking)
    assertEquals(calculation.expiryDate, LocalDate.of(2015, 9, 20))
    assertEquals(calculation.releaseDate, LocalDate.of(2014, 9, 20))
    assertEquals("[SLED, CRD]", calculation.sentence.sentenceTypes.toString())
  }

  @Test
  fun `Example 10`() {
    val sentence = jsonTransformation.loadSentence("3_year_dec_2012")
    sentenceIdentificationService.identify(sentence, offender)
    val offender = Offender("A1234BC", "John Doe", LocalDate.of(1980, 1, 1))
    val adjustments = mutableMapOf<AdjustmentType, Int>()
    adjustments[AdjustmentType.REMAND] = 35
    adjustments[AdjustmentType.TAGGED_BAIL] = 10

    val booking = Booking(offender, mutableListOf(sentence), adjustments)
    val calculation = sentenceCalculationService.calculate(sentence, booking)
    assertEquals(calculation.expiryDate, LocalDate.of(2015, 10, 30))
    assertEquals(calculation.releaseDate, LocalDate.of(2014, 5, 1))
    assertEquals("[SLED, CRD]", calculation.sentence.sentenceTypes.toString())
  }

  @Test
  fun `Example 11`() {
    val sentence = jsonTransformation.loadSentence("8_year_dec_2012")
    sentenceIdentificationService.identify(sentence, offender)
    val adjustments = mutableMapOf<AdjustmentType, Int>()
    adjustments[AdjustmentType.REMAND] = 10
    val offender = Offender("A1234BC", "John Doe", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(sentence), adjustments)
    val calculation = sentenceCalculationService.calculate(sentence, booking)
    assertEquals(calculation.expiryDate, LocalDate.of(2013, 8, 6))
    assertEquals(calculation.releaseDate, LocalDate.of(2013, 4, 7))
    assertEquals("[ARD, SED]", calculation.sentence.sentenceTypes.toString())
  }

  @Test
  fun `Example 12`() {
    val sentence = jsonTransformation.loadSentence("8_year_feb_2015")
    sentenceIdentificationService.identify(sentence, offender)
    val adjustments = mutableMapOf<AdjustmentType, Int>()
    adjustments[AdjustmentType.REMAND] = 21
    val offender = Offender("A1234BC", "John Doe", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(sentence), adjustments)
    val calculation = sentenceCalculationService.calculate(sentence, booking)
    assertEquals(calculation.expiryDate, LocalDate.of(2015, 9, 24))
    assertEquals(calculation.releaseDate, LocalDate.of(2015, 5, 26))
    assertEquals(calculation.topUpSupervisionDate, LocalDate.of(2016, 5, 26))
    assertEquals("[SLED, CRD, TUSED]", calculation.sentence.sentenceTypes.toString())
  }
}
