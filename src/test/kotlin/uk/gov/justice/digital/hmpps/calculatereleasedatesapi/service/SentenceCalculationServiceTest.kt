package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.ersedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.hdced4ConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.hdcedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.releasePointMultiplierConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.*

@ExtendWith(MockitoExtension::class)
class SentenceCalculationServiceTest {

  private val hdcedConfiguration = hdcedConfigurationForTests()
  private val hdcedCalculator = HdcedCalculator(hdcedConfiguration)
  private val bankHolidayService = mock<BankHolidayService>()
  private val workingDayService = WorkingDayService(bankHolidayService)
  private val tusedCalculator = TusedCalculator(workingDayService)
  private val hdced4Configuration = hdced4ConfigurationForTests()
  private val hdced4Calculator = Hdced4Calculator(hdced4Configuration)
  private val ersedCalculator = ErsedCalculator(ersedConfigurationForTests())
  private val releasePointMultiplierLookup = ReleasePointMultiplierLookup(releasePointMultiplierConfigurationForTests("alt-calculation-params"))
  private val featureToggles = FeatureToggles(botus = false, sdsEarlyRelease = true)
  private val sentenceAdjustedCalculationService = SentenceAdjustedCalculationService(hdcedCalculator, tusedCalculator, hdced4Calculator, ersedCalculator)
  private val sentenceCalculationService: SentenceCalculationService = SentenceCalculationService(sentenceAdjustedCalculationService, releasePointMultiplierLookup)
  private val sentenceIdentificationService: SentenceIdentificationService = SentenceIdentificationService(tusedCalculator, hdced4Calculator, featureToggles)
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
    assertEquals("[SLED, CRD, HDCED, HDCED4PLUS]", calculation.sentence.releaseDateTypes.getReleaseDateTypes().toString())
    assertThat(calculation.homeDetentionCurfewEligibilityDate).isNull()
    assertThat(calculation.numberOfDaysToHomeDetentionCurfewEligibilityDate).isZero()
  }


  @ParameterizedTest
  @CsvSource(
    // straddle (commencement date)
    "2024-07-06,2024-07-25,2024-07-15",
    // both before (50%)
    "2024-07-01,2024-07-20,2024-07-10",
    // both after (45%)
    "2024-07-10,2024-07-29,2024-07-18",
  )
  //  val OPERATION_EARLY_DAWN_COMMENCEMENT_DATE: LocalDate = LocalDate.of(2024, 7, 15)
  fun `fudge around with the commencement date for operation early dawn`(sentencedAt: LocalDate, sed: LocalDate, crd: LocalDate) {
    val duration = Duration(mutableMapOf(DAYS to 20L, WEEKS to 0L, MONTHS to 0L, YEARS to 0L))
    val sentence = StandardDeterminateSentence(
      sentencedAt = sentencedAt,
      duration = duration,
      offence = Offence(committedAt = LocalDate.of(2021, 1, 1), offenceCode = "RR1"),
      identifier = UUID.nameUUIDFromBytes(("1-1").toByteArray()),
      consecutiveSentenceUUIDs = emptyList(),
      lineSequence = 1,
      caseSequence = 1,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    sentenceIdentificationService.identify(sentence, offender)
    assertThat(sentence.identificationTrack).isEqualTo(SentenceIdentificationTrack.SDS_EARLY_RELEASE)

    val adjustments = mutableMapOf<AdjustmentType, MutableList<Adjustment>>()
    val offender = Offender("A1234BC", LocalDate.of(1980, 1, 1))
    val booking = Booking(offender, mutableListOf(sentence), Adjustments(adjustments))
    val calculation = sentenceCalculationService.calculate(sentence, booking)
    assertThat(calculation.expiryDate).isEqualTo(sed)
    assertThat(calculation.releaseDate).isEqualTo(crd)
  }

  @BeforeEach
  fun beforeAll() {
    Mockito.`when`(bankHolidayService.getBankHolidays()).thenReturn(CalculationTransactionalServiceTest.cachedBankHolidays)
  }
}
