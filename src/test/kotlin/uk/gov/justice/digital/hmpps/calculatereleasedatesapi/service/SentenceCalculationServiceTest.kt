package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import java.time.LocalDate

class SentenceCalculationServiceTest {

  private val sentenceAdjustedCalculationService = SentenceAdjustedCalculationService()
  private val sentenceCalculationService: SentenceCalculationService = SentenceCalculationService(sentenceAdjustedCalculationService)
  private val sentenceIdentificationService: SentenceIdentificationService = SentenceIdentificationService()
  private val jsonTransformation = JsonTransformation()
  private val offender = jsonTransformation.loadOffender("john_doe")

  @Test
  fun `Example 9`() {
    val sentence = jsonTransformation.loadSentence("2_year_sep_2013")
    sentenceIdentificationService.identify(sentence, offender)
    val offender = Offender("A1234BC", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(sentence), Adjustments())
    val calculation = sentenceCalculationService.calculate(sentence, booking)
    assertEquals(calculation.expiryDate, LocalDate.of(2015, 9, 20))
    assertEquals(calculation.releaseDate, LocalDate.of(2014, 9, 20))
    assertEquals(LocalDate.of(2014, 5, 9), calculation.homeDetentionCurfewEligibilityDate)
    assertEquals("[SLED, CRD, HDCED]", calculation.sentence.releaseDateTypes.toString())
  }

  @Test
  fun `Example 10`() {
    val sentence = jsonTransformation.loadSentence("3_year_dec_2012")
    sentenceIdentificationService.identify(sentence, offender)
    val offender = Offender("A1234BC", LocalDate.of(1980, 1, 1))
    val adjustments = mutableMapOf<AdjustmentType, MutableList<Adjustment>>()
    adjustments[AdjustmentType.REMAND] = mutableListOf(Adjustment(appliesToSentencesFrom = sentence.sentencedAt, numberOfDays = 35))
    adjustments[AdjustmentType.TAGGED_BAIL] = mutableListOf(Adjustment(appliesToSentencesFrom = sentence.sentencedAt, numberOfDays = 10))

    val booking = Booking(offender, mutableListOf(sentence), Adjustments(adjustments))
    val calculation = sentenceCalculationService.calculate(sentence, booking)
    assertEquals(LocalDate.of(2015, 10, 30), calculation.expiryDate)
    assertEquals(LocalDate.of(2014, 5, 1), calculation.releaseDate)
    assertEquals(LocalDate.of(2013, 12, 18), calculation.homeDetentionCurfewEligibilityDate)
    assertEquals("[SLED, CRD, HDCED]", calculation.sentence.releaseDateTypes.toString())
  }

  @Test
  fun `Example 11`() {
    val sentence = jsonTransformation.loadSentence("8_month_dec_2012")
    sentenceIdentificationService.identify(sentence, offender)
    val adjustments = mutableMapOf<AdjustmentType, MutableList<Adjustment>>()
    adjustments[AdjustmentType.REMAND] = mutableListOf(Adjustment(appliesToSentencesFrom = sentence.sentencedAt, numberOfDays = 10))
    val offender = Offender("A1234BC", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(sentence), Adjustments(adjustments))
    val calculation = sentenceCalculationService.calculate(sentence, booking)
    assertEquals(LocalDate.of(2013, 8, 6), calculation.expiryDate)
    assertEquals(LocalDate.of(2013, 4, 7), calculation.releaseDate)
    assertEquals(LocalDate.of(2013, 2, 6), calculation.homeDetentionCurfewEligibilityDate)
    assertEquals("[ARD, SED, HDCED]", calculation.sentence.releaseDateTypes.toString())
  }

  @Test
  fun `Example 12`() {
    val sentence = jsonTransformation.loadSentence("8_month_feb_2015")
    sentenceIdentificationService.identify(sentence, offender)
    val adjustments = mutableMapOf<AdjustmentType, MutableList<Adjustment>>()
    adjustments[AdjustmentType.REMAND] = mutableListOf(Adjustment(appliesToSentencesFrom = sentence.sentencedAt, numberOfDays = 21))
    val offender = Offender("A1234BC", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(sentence), Adjustments(adjustments))
    val calculation = sentenceCalculationService.calculate(sentence, booking)
    assertEquals(LocalDate.of(2015, 9, 24), calculation.expiryDate)
    assertEquals(LocalDate.of(2015, 5, 26), calculation.releaseDate)
    assertEquals(LocalDate.of(2016, 5, 26), calculation.topUpSupervisionDate)
    assertEquals(LocalDate.of(2015, 3, 28), calculation.homeDetentionCurfewEligibilityDate)
    assertEquals("[SLED, CRD, TUSED, HDCED]", calculation.sentence.releaseDateTypes.toString())
  }

  @Test
  fun `5 year sentence no HDCED`() {
    val sentence = jsonTransformation.loadSentence("5_year_march_2017")
    sentenceIdentificationService.identify(sentence, offender)
    val adjustments = mutableMapOf<AdjustmentType, MutableList<Adjustment>>()
    adjustments[AdjustmentType.REMAND] = mutableListOf(Adjustment(appliesToSentencesFrom = sentence.sentencedAt, numberOfDays = 21))
    val offender = Offender("A1234BC", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(sentence), Adjustments(adjustments))
    val calculation = sentenceCalculationService.calculate(sentence, booking)
    assertEquals(LocalDate.of(2022, 2, 22), calculation.expiryDate)
    assertEquals(LocalDate.of(2019, 8, 24), calculation.releaseDate)
    assertEquals("[SLED, CRD]", calculation.sentence.releaseDateTypes.toString())
  }
}
