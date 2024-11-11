package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.ersedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.hdcedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.releasePointMultiplierConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class SentenceCalculationServiceTest {

  private val hdcedConfiguration = hdcedConfigurationForTests()
  private val bankHolidayService = mock<BankHolidayService>()
  private val workingDayService = WorkingDayService(bankHolidayService)
  private val tusedCalculator = TusedCalculator(workingDayService)
  private val sentenceAggregator = SentenceAggregator()
  private val releasePointMultiplierLookup = ReleasePointMultiplierLookup(releasePointMultiplierConfigurationForTests())
  private val hdcedCalculator = HdcedCalculator(hdcedConfiguration)
  private val ersedCalculator = ErsedCalculator(ersedConfigurationForTests())
  private val sentenceAdjustedCalculationService =
    SentenceAdjustedCalculationService(tusedCalculator, hdcedCalculator, ersedCalculator)
  private val sentenceCalculationService: SentenceCalculationService =
    SentenceCalculationService(sentenceAdjustedCalculationService, releasePointMultiplierLookup, sentenceAggregator)
  private val sentenceIdentificationService: SentenceIdentificationService =
    SentenceIdentificationService(tusedCalculator, hdcedCalculator)
  private val jsonTransformation = JsonTransformation()
  private val offender = jsonTransformation.loadOffender("john_doe")

  @Test
  fun `Example 9`() {
    val sentence = jsonTransformation.loadSentence("2_year_sep_2013")
    sentenceIdentificationService.identify(
      sentence,
      offender,
      CalculationOptions(calculateErsed = false, allowSDSEarlyRelease = false),
    )
    val offender = Offender("A1234BC", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(sentence), Adjustments())
    val calculation = sentenceCalculationService.calculate(
      sentence,
      booking,
      CalculationOptions(calculateErsed = false, allowSDSEarlyRelease = false),
    )
    assertEquals(calculation.expiryDate, LocalDate.of(2015, 9, 20))
    assertEquals(calculation.releaseDate, LocalDate.of(2014, 9, 20))
    assertEquals(LocalDate.of(2014, 3, 25), calculation.homeDetentionCurfewEligibilityDate)
    assertEquals("[SLED, CRD, HDCED]", calculation.sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  @Test
  fun `Example 10`() {
    val sentence = jsonTransformation.loadSentence("3_year_dec_2012")
    sentenceIdentificationService.identify(
      sentence,
      offender,
      CalculationOptions(calculateErsed = false, allowSDSEarlyRelease = false),
    )
    val offender = Offender("A1234BC", LocalDate.of(1980, 1, 1))
    val adjustments = mutableMapOf<AdjustmentType, MutableList<Adjustment>>()
    adjustments[AdjustmentType.REMAND] =
      mutableListOf(Adjustment(appliesToSentencesFrom = sentence.sentencedAt, numberOfDays = 35))
    adjustments[AdjustmentType.TAGGED_BAIL] =
      mutableListOf(Adjustment(appliesToSentencesFrom = sentence.sentencedAt, numberOfDays = 10))

    val booking = Booking(offender, mutableListOf(sentence), Adjustments(adjustments))
    val calculation = sentenceCalculationService.calculate(
      sentence,
      booking,
      CalculationOptions(calculateErsed = false, allowSDSEarlyRelease = false),
    )
    assertEquals(LocalDate.of(2015, 10, 30), calculation.expiryDate)
    assertEquals(LocalDate.of(2014, 5, 1), calculation.releaseDate)
    assertEquals(LocalDate.of(2013, 11, 3), calculation.homeDetentionCurfewEligibilityDate)
    assertEquals("[SLED, CRD, HDCED]", calculation.sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  @Test
  fun `Example 11`() {
    val sentence = jsonTransformation.loadSentence("8_month_dec_2012")
    sentenceIdentificationService.identify(
      sentence,
      offender,
      CalculationOptions(calculateErsed = false, allowSDSEarlyRelease = false),
    )
    val adjustments = mutableMapOf<AdjustmentType, MutableList<Adjustment>>()
    adjustments[AdjustmentType.REMAND] =
      mutableListOf(Adjustment(appliesToSentencesFrom = sentence.sentencedAt, numberOfDays = 10))
    val offender = Offender("A1234BC", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(sentence), Adjustments(adjustments))
    val calculation = sentenceCalculationService.calculate(
      sentence,
      booking,
      CalculationOptions(calculateErsed = false, allowSDSEarlyRelease = false),
    )
    assertEquals(LocalDate.of(2013, 8, 6), calculation.expiryDate)
    assertEquals(LocalDate.of(2013, 4, 7), calculation.releaseDate)
    assertEquals(LocalDate.of(2013, 2, 6), calculation.homeDetentionCurfewEligibilityDate)
    assertEquals("[ARD, SED, HDCED]", calculation.sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  @Test
  fun `Example 12`() {
    val sentence = jsonTransformation.loadSentence("8_month_feb_2015")
    sentenceIdentificationService.identify(
      sentence,
      offender,
      CalculationOptions(calculateErsed = false, allowSDSEarlyRelease = false),
    )
    val adjustments = mutableMapOf<AdjustmentType, MutableList<Adjustment>>()
    adjustments[AdjustmentType.REMAND] =
      mutableListOf(Adjustment(appliesToSentencesFrom = sentence.sentencedAt, numberOfDays = 21))
    val offender = Offender("A1234BC", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(sentence), Adjustments(adjustments))

    val calculation = sentenceCalculationService.calculate(
      sentence,
      booking,
      CalculationOptions(calculateErsed = false, allowSDSEarlyRelease = false),
    )
    assertEquals(LocalDate.of(2015, 9, 24), calculation.expiryDate)
    assertEquals(LocalDate.of(2015, 5, 26), calculation.releaseDate)
    assertEquals(LocalDate.of(2016, 5, 26), calculation.topUpSupervisionDate)
    assertEquals(LocalDate.of(2015, 3, 28), calculation.homeDetentionCurfewEligibilityDate)
    assertEquals("[SLED, CRD, TUSED, HDCED]", calculation.sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  @Test
  fun `Example 13`() {
    val sentence = jsonTransformation.loadBotusSentence("8_month_feb_2015_botus")
    sentenceIdentificationService.identify(
      sentence,
      offender,
      CalculationOptions(calculateErsed = false, allowSDSEarlyRelease = false),
    )
    val adjustments = mutableMapOf<AdjustmentType, MutableList<Adjustment>>()
    adjustments[AdjustmentType.REMAND] =
      mutableListOf(Adjustment(appliesToSentencesFrom = sentence.sentencedAt, numberOfDays = 21))

    val offender = Offender("A1234BC", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(sentence), Adjustments(adjustments))

    val calculation = sentenceCalculationService.calculate(
      sentence,
      booking,
      CalculationOptions(calculateErsed = false, allowSDSEarlyRelease = false),
    )
    assertEquals(LocalDate.of(2015, 10, 15), calculation.expiryDate)
    assertEquals(LocalDate.of(2015, 10, 15), calculation.releaseDate)
    assertEquals(calculation.homeDetentionCurfewEligibilityDate, null)
    assertEquals(LocalDate.of(2016, 1, 16), calculation.topUpSupervisionDate) // use historic TUSED
    assertEquals("[ARD, SED, TUSED]", calculation.sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  @Test
  fun `Example 14`() {
    val sentence = jsonTransformation.loadBotusSentence("8_month_feb_2015_botus")
    sentence.latestTusedDate = LocalDate.of(2015, 1, 15) // TUSED prior to RELEASE should be ignored
    sentenceIdentificationService.identify(
      sentence,
      offender,
      CalculationOptions(calculateErsed = false, allowSDSEarlyRelease = false),
    )
    val adjustments = mutableMapOf<AdjustmentType, MutableList<Adjustment>>()
    adjustments[AdjustmentType.REMAND] =
      mutableListOf(Adjustment(appliesToSentencesFrom = sentence.sentencedAt, numberOfDays = 21))
    val offender = Offender("A1234BC", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(sentence), Adjustments(adjustments))

    val calculation = sentenceCalculationService.calculate(
      sentence,
      booking,
      CalculationOptions(calculateErsed = false, allowSDSEarlyRelease = false),
    )
    assertEquals(LocalDate.of(2015, 10, 15), calculation.expiryDate)
    assertEquals(LocalDate.of(2015, 10, 15), calculation.releaseDate)
    assertEquals(calculation.homeDetentionCurfewEligibilityDate, null)
    assertEquals(null, calculation.topUpSupervisionDate) // should not use historic TUSED
    assertEquals("[ARD, SED, TUSED]", calculation.sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  @BeforeEach
  fun beforeAll() {
    Mockito.`when`(bankHolidayService.getBankHolidays())
      .thenReturn(CalculationTransactionalServiceTest.cachedBankHolidays)
  }
}
