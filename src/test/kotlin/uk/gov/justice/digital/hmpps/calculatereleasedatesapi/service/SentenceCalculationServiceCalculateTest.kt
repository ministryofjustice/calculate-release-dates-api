package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import java.time.LocalDate

class SentenceCalculationServiceCalculateTest {

  private val sentenceCalculationService: SentenceCalculationService = SentenceCalculationService()
  private val jsonTransformation = JsonTransformation()
  private val offender = jsonTransformation.loadOffender("john_doe")

  @Test
  fun `Example 9`() {
    val sentence = jsonTransformation.loadSentence("2_year_sep_2013")
    sentenceCalculationService.identify(sentence, offender)
    val calculation = sentenceCalculationService.calculate(sentence)
    assertEquals(calculation.expiryDate, LocalDate.of(2015, 9, 20))
    assertEquals(calculation.releaseDate, LocalDate.of(2014, 9, 20))
    assertEquals("[SLED, CRD]", calculation.sentence.sentenceTypes.toString())
  }

  @Test
  fun `Example 10`() {
    val sentence = jsonTransformation.loadSentence("3_year_dec_2012")
    sentenceCalculationService.identify(sentence, offender)
    val calculation = sentenceCalculationService.calculate(sentence)
    assertEquals(calculation.expiryDate, LocalDate.of(2015, 10, 30))
    assertEquals(calculation.releaseDate, LocalDate.of(2014, 5, 1))
    assertEquals("[SLED, CRD]", calculation.sentence.sentenceTypes.toString())
  }

  @Test
  fun `Example 11`() {
    val sentence = jsonTransformation.loadSentence("8_year_dec_2012")
    sentenceCalculationService.identify(sentence, offender)
    val calculation = sentenceCalculationService.calculate(sentence)
    assertEquals(calculation.expiryDate, LocalDate.of(2013, 8, 6))
    assertEquals(calculation.releaseDate, LocalDate.of(2013, 4, 7))
    assertEquals("[ARD, SED]", calculation.sentence.sentenceTypes.toString())
  }

  @Test
  fun `Example 12`() {
    val sentence = jsonTransformation.loadSentence("8_year_feb_2015")
    sentenceCalculationService.identify(sentence, offender)
    val calculation = sentenceCalculationService.calculate(sentence)
    assertEquals(calculation.expiryDate, LocalDate.of(2015, 9, 24))
    assertEquals(calculation.releaseDate, LocalDate.of(2015, 5, 26))
    assertEquals(calculation.topUpSupervisionDate, LocalDate.of(2016, 5, 26))
    assertEquals("[SLED, CRD, TUSED]", calculation.sentence.sentenceTypes.toString())
  }
}
