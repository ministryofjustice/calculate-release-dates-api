package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHoliday
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.RegionBankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@ExtendWith(MockitoExtension::class)
class SentenceCalculationServiceTest {

  private val hdcedConfiguration = HdcedCalculator.HdcedConfiguration(12, ChronoUnit.WEEKS, 4, ChronoUnit.YEARS, 14, 720, ChronoUnit.DAYS, 179)
  private val hdcedCalculator = HdcedCalculator(hdcedConfiguration)
  private val bankHolidayService = mock<BankHolidayService>()
  private val workingDayService = WorkingDayService(bankHolidayService)
  private val tusedCalculator = TusedCalculator(workingDayService)
  private val hdced4configuration = Hdced4Calculator.Hdced4Configuration(12, ChronoUnit.WEEKS, 14, 720, ChronoUnit.DAYS, 179)
  private val hdced4Calculator = Hdced4Calculator(hdced4configuration)
  private val ersedConfiguration = ErsedCalculator.ErsedConfiguration(2180, 1635, 544)
  private val ersedCalculator = ErsedCalculator(ersedConfiguration)
  private val sentenceAdjustedCalculationService = SentenceAdjustedCalculationService(hdcedCalculator, tusedCalculator, hdced4Calculator, ersedCalculator)
  private val sentenceCalculationService: SentenceCalculationService = SentenceCalculationService(sentenceAdjustedCalculationService)
  private val sentenceIdentificationService: SentenceIdentificationService = SentenceIdentificationService(hdcedCalculator, tusedCalculator, hdced4Calculator)
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
    assertEquals(LocalDate.of(2014, 3, 25), calculation.homeDetentionCurfewEligibilityDate)
    assertEquals(LocalDate.of(2014, 3, 25), calculation.homeDetentionCurfew4PlusEligibilityDate)
    assertEquals("[SLED, CRD, HDCED, HDCED4PLUS]", calculation.sentence.releaseDateTypes.getReleaseDateTypes().toString())
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
    assertEquals(LocalDate.of(2013, 11, 3), calculation.homeDetentionCurfewEligibilityDate)
    assertEquals(LocalDate.of(2013, 11, 3), calculation.homeDetentionCurfew4PlusEligibilityDate)
    assertEquals("[SLED, CRD, HDCED, HDCED4PLUS]", calculation.sentence.releaseDateTypes.getReleaseDateTypes().toString())
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
    assertEquals(LocalDate.of(2013, 2, 6), calculation.homeDetentionCurfew4PlusEligibilityDate)
    assertEquals("[ARD, SED, HDCED, HDCED4PLUS]", calculation.sentence.releaseDateTypes.getReleaseDateTypes().toString())
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
    assertEquals(LocalDate.of(2015, 3, 28), calculation.homeDetentionCurfew4PlusEligibilityDate)
    assertEquals("[SLED, CRD, TUSED, HDCED, HDCED4PLUS]", calculation.sentence.releaseDateTypes.getReleaseDateTypes().toString())
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
    assertEquals("[SLED, CRD, HDCED4PLUS]", calculation.sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  @BeforeEach
  fun beforeAll() {
    Mockito.`when`(bankHolidayService.getBankHolidays()).thenReturn(CalculationTransactionalServiceTest.cachedBankHolidays)
  }

  companion object {
    val cachedBankHolidays =
      BankHolidays(
        RegionBankHolidays(
          "England and Wales",
          listOf(
            BankHoliday("Christmas Day Bank Holiday", LocalDate.of(2021, 12, 27)),
            BankHoliday("Boxing Day Bank Holiday", LocalDate.of(2021, 12, 28)),
          ),
        ),
        RegionBankHolidays("Scotland", emptyList()),
        RegionBankHolidays("Northern Ireland", emptyList()),
      )
  }
}
