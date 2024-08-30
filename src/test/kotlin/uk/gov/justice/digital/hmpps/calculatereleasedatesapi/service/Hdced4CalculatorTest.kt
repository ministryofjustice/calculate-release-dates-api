package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.hdcedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.HdcedConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

@ExtendWith(MockitoExtension::class)
class Hdced4CalculatorTest {

  private val config: HdcedConfiguration = hdcedConfigurationForTests()
  private val releaseDateMultiplierLookup = mock<ReleasePointMultiplierLookup>()
  private val calculator = Hdced4Calculator(config, SentenceAggregator(), releaseDateMultiplierLookup)

  @BeforeEach
  fun setUp() {
    whenever(releaseDateMultiplierLookup.multiplierFor(SentenceIdentificationTrack.SDS_PLUS_RELEASE)).thenReturn(0.66666)
    whenever(releaseDateMultiplierLookup.multiplierFor(SentenceIdentificationTrack.SDS_STANDARD_RELEASE)).thenReturn(0.5)
  }

  @Test
  fun `shouldn't calculate a date for a sex offender`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.DAYS to 150L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)
    val sentenceCalculation = sentenceCalculation(sentence, 150, 75, Adjustments())

    calc(sentenceCalculation, sentence, isActiveSexOffender = true)

    assertHasNoHDCED(sentenceCalculation)
  }

  @Test
  fun `shouldn't calculate a date when the custodial period is less than the minimum`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.WEEKS to 11L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)
    val sentenceCalculation = sentenceCalculation(sentence, 77, 39, Adjustments())

    calc(sentenceCalculation, sentence)

    assertHasNoHDCED(sentenceCalculation)
  }

  @Test
  fun `minimum custodial period is inclusive`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.DAYS to config.minimumCustodialPeriodDays * 2))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)
    val sentenceCalculation = sentenceCalculation(sentence, config.minimumCustodialPeriodDays.toInt() * 2, config.minimumCustodialPeriodDays.toInt(), Adjustments())

    calc(sentenceCalculation, sentence)

    assertHasHDCED(sentenceCalculation)
  }

  @ParameterizedTest
  @CsvSource(
    "210,0.2,true",
    "205,0.2,false",
    "84,0.5,true",
    "82,0.5,false",
    "94,0.45,true",
    "91,0.45,false",
  )
  fun `minimum custodial period handles different release points`(sentenceLength: Int, releasePointMultiplier: Double, hasHDCED: Boolean) {
    val numberOfDaysToDeterminateRelease = ceil(sentenceLength.toDouble() * releasePointMultiplier).toInt()
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.DAYS to sentenceLength.toLong()))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)
    val sentenceCalculation = sentenceCalculation(sentence, sentenceLength, numberOfDaysToDeterminateRelease, Adjustments())

    calc(sentenceCalculation, sentence)

    if (hasHDCED) {
      assertHasHDCED(sentenceCalculation)
    } else {
      assertHasNoHDCED(sentenceCalculation)
    }
  }

  @Test
  fun `shouldn't calculate a date when the adjusted release date is less than the minimum custodial period`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.WEEKS to 20L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)

    val withLargeRemand = Adjustments(mutableMapOf(AdjustmentType.REMAND to mutableListOf(Adjustment(sentencedAt, 65))))
    val sentenceCalculation = sentenceCalculation(sentence, 140, 70, withLargeRemand)

    calc(sentenceCalculation, sentence)

    assertHasNoHDCED(sentenceCalculation)
  }

  @Test
  fun `should calculate a date with quarter of sentence length if the sentence length is greater than the minimum and less than midpoint`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.WEEKS to 20L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)

    val sentenceCalculation = sentenceCalculation(sentence, 140, 70, Adjustments())

    calc(sentenceCalculation, sentence)

    assertThat(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate).isEqualTo(LocalDate.of(2020, 2, 5))
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate).isEqualTo(35L)
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS]).isEqualTo(
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT),
        rulesWithExtraAdjustments = mapOf(
          CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT to AdjustmentDuration(35, ChronoUnit.DAYS),
        ),
        adjustedDays = 0,
        releaseDate = LocalDate.of(2020, 2, 5),
        unadjustedDate = LocalDate.of(2020, 1, 1),
      ),
    ) // Shows hint as Sentence date plus HDCED adjustment (35) plus/minus regular adjustment (0)
  }

  @ParameterizedTest
  @CsvSource(
    "140,0.5,2020-02-05,35",
    "300,0.2,2020-01-31,30",
    "84,0.5,2020-01-29,28",
    "200,0.45,2020-02-15,45",
  )
  fun `below midpoint calculation should handle different release points`(sentenceLength: Int, releasePointMultiplier: Double, expectedHDCED: LocalDate, expectedNumberOfDaysToHDCED: Long) {
    val numberOfDaysToDeterminateRelease = ceil(sentenceLength.toDouble() * releasePointMultiplier).toInt()
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.DAYS to sentenceLength.toLong()))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)

    val sentenceCalculation = sentenceCalculation(sentence, sentenceLength, numberOfDaysToDeterminateRelease, Adjustments())

    calc(sentenceCalculation, sentence)

    assertThat(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate).isEqualTo(expectedHDCED)
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate).isEqualTo(expectedNumberOfDaysToHDCED)
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS]?.rules?.first()).isEqualTo(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT)
  }

  @Test
  fun `extra days for SDS consecutive to BOTUS are added correctly`() {
    val sentence = StandardDeterminateSentence(
      Offence(LocalDate.of(2020, 1, 1)),
      Duration(mapOf(ChronoUnit.DAYS to 140.toLong())),
      LocalDate.of(2020, 1, 1),
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )

    val sentenceCalculation =
      sentenceCalculation(sentence, numberOfDaysToSED = 140, numberOfDaysToDeterminateReleaseDate = 70, Adjustments())

    calc(sentenceCalculation, sentence)

    assertThat(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate).isEqualTo(LocalDate.of(2020, 2, 5))

    calc(sentenceCalculation, sentence, extraDaysForSdsConsecutiveToBotus = 14)

    assertThat(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate).isEqualTo(LocalDate.of(2020, 2, 19))
  }

  @Test
  fun `should calculate a date with 28 days rules if the sentence length is greater than the minimum and less than midpoint and quarter is less than 28`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.DAYS to 106L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)

    val sentenceCalculation = sentenceCalculation(sentence, 106, 53, Adjustments())

    calc(sentenceCalculation, sentence)

    assertThat(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate).isEqualTo(LocalDate.of(2020, 1, 29))
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate).isEqualTo(28L)
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS]).isEqualTo(
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT),
        rulesWithExtraAdjustments = mapOf(
          CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT to AdjustmentDuration(28, ChronoUnit.DAYS),
        ),
        adjustedDays = 0,
        releaseDate = LocalDate.of(2020, 1, 29),
        unadjustedDate = LocalDate.of(2020, 1, 1),
      ),
    ) // Shows hint as Sentence date plus HDCED adjustment (35) plus/minus regular adjustment (0)
  }

  @Test
  fun `should enforce minimum custodial period if sentence is greater than the minimum and less than midpoint but adjustments reduce to less than min custodial period`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.WEEKS to 20L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)

    val minus25DayAdjustments = Adjustments(mutableMapOf(AdjustmentType.REMAND to mutableListOf(Adjustment(sentencedAt, 25))))
    val sentenceCalculation = sentenceCalculation(sentence, 140, 70, minus25DayAdjustments)

    calc(sentenceCalculation, sentence)

    assertThat(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate).isEqualTo(LocalDate.of(2020, 1, 15))
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate).isEqualTo(14L)
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS]).isEqualTo(
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD, CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT),
        rulesWithExtraAdjustments = mapOf(
          CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(14, ChronoUnit.DAYS),
        ),
        adjustedDays = 0,
        releaseDate = LocalDate.of(2020, 1, 15),
        unadjustedDate = LocalDate.of(2020, 1, 1),
      ),
    )
  }

  @Test
  fun `should calculate a date CRD minus configured deduction period if the sentence length is greater than the midpoint`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.DAYS to 722L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)

    val sentenceCalculation = sentenceCalculation(sentence, 722, 361, Adjustments())

    calc(sentenceCalculation, sentence)

    assertThat(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate).isEqualTo(LocalDate.of(2020, 6, 30))
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate).isEqualTo(181L)
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS]).isEqualTo(
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD),
        rulesWithExtraAdjustments = mapOf(
          CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD to AdjustmentDuration(-179, ChronoUnit.DAYS),
        ),
        adjustedDays = 0,
        releaseDate = LocalDate.of(2020, 6, 30),
        unadjustedDate = LocalDate.of(2020, 12, 26),
      ),
    ) // Hint shown as adjusted CRD minus 179 days
  }

  @Test
  fun `exact midpoint should use above midpoint calculation method`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.DAYS to 1000L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)

    val sentenceCalculation = sentenceCalculation(sentence, 1000, config.custodialPeriodMidPointDays.toInt(), Adjustments())

    calc(sentenceCalculation, sentence)

    assertThat(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate).isEqualTo(LocalDate.of(2020, 6, 29))
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate).isEqualTo(180L)
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS]?.rules?.first()).isEqualTo(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD)
  }

  @Test
  fun `should calculate a date with minimum custodial period if the sentence length is greater than the midpoint`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.DAYS to 722L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)

    val adjustedLessThanMinCustodialPeriod = Adjustments(mutableMapOf(AdjustmentType.REMAND to mutableListOf(Adjustment(sentencedAt, 180))))
    val sentenceCalculation = sentenceCalculation(sentence, 722, 361, adjustedLessThanMinCustodialPeriod)

    calc(sentenceCalculation, sentence)

    assertThat(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate).isEqualTo(LocalDate.of(2020, 1, 15))
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate).isEqualTo(14L)
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS]).isEqualTo(
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD, CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD),
        rulesWithExtraAdjustments = mapOf(
          CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(14, ChronoUnit.DAYS),
        ),
        adjustedDays = 0,
        releaseDate = LocalDate.of(2020, 1, 15),
        unadjustedDate = LocalDate.of(2020, 1, 1),
      ),
    )
  }

  @Test
  fun `Non SDS+ consecutive chain should not produce an HDC if total is less than minimum custodial period`() {
    val sentencedAt = LocalDate.of(2024, 3, 15)
    val duration = Duration(mapOf(ChronoUnit.MONTHS to 1L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val firstSentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)
    val secondSentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)
    val consecSentence = ConsecutiveSentence(listOf(firstSentence, secondSentence))

    firstSentence.sentenceCalculation = sentenceCalculation(firstSentence, 31, 16, Adjustments())
    secondSentence.sentenceCalculation = sentenceCalculation(secondSentence, 31, 16, Adjustments())
    consecSentence.sentenceCalculation = sentenceCalculation(consecSentence, 62, 31, Adjustments())

    calc(consecSentence.sentenceCalculation, consecSentence)

    assertHasNoHDCED(consecSentence.sentenceCalculation)
  }

  @Test
  fun `Non SDS+ consecutive chain should produce an HDC if individual is less than minimum custodial period but aggregated is more`() {
    val sentencedAt = LocalDate.of(2024, 3, 19)
    val duration = Duration(mapOf(ChronoUnit.MONTHS to 2L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val firstSentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)
    val secondSentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)
    val consecSentence = ConsecutiveSentence(listOf(firstSentence, secondSentence))

    val minus20DayAdjustments = Adjustments(mutableMapOf(AdjustmentType.REMAND to mutableListOf(Adjustment(sentencedAt, 20, LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 18)))))
    firstSentence.sentenceCalculation = sentenceCalculation(firstSentence, firstSentence.getLengthInDays(), firstSentence.getLengthInDays() / 2, minus20DayAdjustments)
    secondSentence.sentenceCalculation = sentenceCalculation(secondSentence, secondSentence.getLengthInDays(), secondSentence.getLengthInDays() / 2, Adjustments())
    consecSentence.sentenceCalculation = sentenceCalculation(consecSentence, consecSentence.getLengthInDays(), consecSentence.getLengthInDays() / 2, minus20DayAdjustments)

    calc(consecSentence.sentenceCalculation, consecSentence)

    assertHasHDCED(consecSentence.sentenceCalculation)
    assertThat(consecSentence.sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate).isEqualTo(LocalDate.of(2024, 4, 2))
  }

  @Test
  fun `Any booking with an SDS+ sentence should not have a HDCED4 date`() {
    val sentencedAt = LocalDate.of(2024, 2, 16)
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val firstSentence = StandardDeterminateSentence(offence, Duration(mapOf(ChronoUnit.MONTHS to 1L)), sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)
    val secondSentence = StandardDeterminateSentence(offence, Duration(mapOf(ChronoUnit.MONTHS to 1L)), sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)
    val thirdSentence = StandardDeterminateSentence(offence, Duration(mapOf(ChronoUnit.YEARS to 6L)), sentencedAt, isSDSPlus = true, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)
    val consecSentence = ConsecutiveSentence(listOf(firstSentence, secondSentence, thirdSentence))

    val numberOfDaysToDeterminateReleaseDateFirst = firstSentence.getLengthInDays() / 2
    val numberOfDaysToDeterminateReleaseDateSecond = secondSentence.getLengthInDays() / 2
    val numberOfDaysToDeterminateReleaseDateThird = (thirdSentence.getLengthInDays() * 0.66).toInt()
    firstSentence.sentenceCalculation = sentenceCalculation(firstSentence, firstSentence.getLengthInDays(), numberOfDaysToDeterminateReleaseDateFirst, Adjustments())
    secondSentence.sentenceCalculation = sentenceCalculation(secondSentence, secondSentence.getLengthInDays(), numberOfDaysToDeterminateReleaseDateSecond, Adjustments())
    thirdSentence.sentenceCalculation = sentenceCalculation(thirdSentence, thirdSentence.getLengthInDays(), numberOfDaysToDeterminateReleaseDateThird, Adjustments())
    firstSentence.identificationTrack = SentenceIdentificationTrack.SDS_STANDARD_RELEASE
    secondSentence.identificationTrack = SentenceIdentificationTrack.SDS_STANDARD_RELEASE
    thirdSentence.identificationTrack = SentenceIdentificationTrack.SDS_PLUS_RELEASE
    consecSentence.sentenceCalculation =
      sentenceCalculation(consecSentence, consecSentence.getLengthInDays(), numberOfDaysToDeterminateReleaseDateFirst + numberOfDaysToDeterminateReleaseDateSecond + numberOfDaysToDeterminateReleaseDateThird, Adjustments())

    calc(consecSentence.sentenceCalculation, consecSentence)

    assertHasNoHDCED(consecSentence.sentenceCalculation)
  }

  private fun assertHasNoHDCED(sentenceCalculation: SentenceCalculation) {
    assertThat(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate).isNull()
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate).isZero()
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS]).isNull()
  }

  private fun assertHasHDCED(sentenceCalculation: SentenceCalculation) {
    assertThat(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate).isNotNull()
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate).isPositive()
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS]).isNotNull
  }

  private fun sentenceCalculation(sentence: CalculableSentence, numberOfDaysToSED: Int, numberOfDaysToDeterminateReleaseDate: Int, adjustments: Adjustments) =
    SentenceCalculation(
      sentence,
      numberOfDaysToSED,
      numberOfDaysToDeterminateReleaseDate,
      numberOfDaysToDeterminateReleaseDate.toDouble(),
      numberOfDaysToDeterminateReleaseDate,
      numberOfDaysToDeterminateReleaseDate.toDouble(),
      sentence.sentencedAt.plusDays(numberOfDaysToDeterminateReleaseDate.toLong() - 1),
      sentence.sentencedAt.plusDays(numberOfDaysToSED.toLong() - 1),
      sentence.sentencedAt.plusDays(numberOfDaysToDeterminateReleaseDate.toLong() - 1),
      null,
      null,
      false,
      adjustments,
      returnToCustodyDate = null,
      numberOfDaysToParoleEligibilityDate = null,
    )

  private fun calc(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    isActiveSexOffender: Boolean = false,
    extraDaysForSdsConsecutiveToBotus: Int = 0,
  ) {
    val offender = Offender("ABC123", LocalDate.of(1980, 1, 1), isActiveSexOffender = isActiveSexOffender)
    if (calculator.doesHdced4DateApply(sentence, offender)) {
      calculator.calculateHdced4(sentence, sentenceCalculation, extraDaysForSdsConsecutiveToBotus, false)
    }
  }
}
