package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.hdcedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.HdcedConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

class HdcedCalculatorTest {

  private val config: HdcedConfiguration = hdcedConfigurationForTests()
  private val calculator = HdcedCalculator(config)

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
  fun `shouldn't calculate a date when the sentence length is greater than the maximum`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.YEARS to 5L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)
    val sentenceCalculation = sentenceCalculation(sentence, 1825, 913, Adjustments())

    calc(sentenceCalculation, sentence)

    assertHasHDCED(sentenceCalculation)
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

    assertThat(sentenceCalculation.homeDetentionCurfewEligibilityDate).isEqualTo(LocalDate.of(2020, 2, 5))
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate).isEqualTo(35L)
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED]).isEqualTo(
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

    assertThat(sentenceCalculation.homeDetentionCurfewEligibilityDate).isEqualTo(expectedHDCED)
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate).isEqualTo(expectedNumberOfDaysToHDCED)
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED]?.rules?.first()).isEqualTo(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT)
  }

  @Test
  fun `should calculate a date with 28 days rules if the sentence length is greater than the minimum and less than midpoint and quarter is less than 28`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.DAYS to 106L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)

    val sentenceCalculation = sentenceCalculation(sentence, 106, 53, Adjustments())

    calc(sentenceCalculation, sentence)

    assertThat(sentenceCalculation.homeDetentionCurfewEligibilityDate).isEqualTo(LocalDate.of(2020, 1, 29))
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate).isEqualTo(28L)
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED]).isEqualTo(
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

    assertThat(sentenceCalculation.homeDetentionCurfewEligibilityDate).isEqualTo(LocalDate.of(2020, 1, 15))
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate).isEqualTo(14L)
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED]).isEqualTo(
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
  fun `should calculate a date CRD minus configured deduction period if the sentence length is greater than the midpoint and less than maximum`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.DAYS to 722L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)

    val sentenceCalculation = sentenceCalculation(sentence, 722, 361, Adjustments())

    calc(sentenceCalculation, sentence)

    assertThat(sentenceCalculation.homeDetentionCurfewEligibilityDate).isEqualTo(LocalDate.of(2020, 6, 30))
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate).isEqualTo(181L)
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED]).isEqualTo(
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

    assertThat(sentenceCalculation.homeDetentionCurfewEligibilityDate).isEqualTo(LocalDate.of(2020, 6, 29))
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate).isEqualTo(180L)
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED]?.rules?.first()).isEqualTo(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD)
  }

  @Test
  fun `should calculate a date with minimum custodial period if the sentence length is greater than the midpoint and less than maximum`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.DAYS to 722L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt, isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)

    val adjustedLessThanMinCustodialPeriod = Adjustments(mutableMapOf(AdjustmentType.REMAND to mutableListOf(Adjustment(sentencedAt, 180))))
    val sentenceCalculation = sentenceCalculation(sentence, 722, 361, adjustedLessThanMinCustodialPeriod)

    calc(sentenceCalculation, sentence)

    assertThat(sentenceCalculation.homeDetentionCurfewEligibilityDate).isEqualTo(LocalDate.of(2020, 1, 15))
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate).isEqualTo(14L)
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED]).isEqualTo(
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

  private fun assertHasNoHDCED(sentenceCalculation: SentenceCalculation) {
    assertThat(sentenceCalculation.homeDetentionCurfewEligibilityDate).isNull()
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate).isZero()
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED]).isNull()
  }

  private fun assertHasHDCED(sentenceCalculation: SentenceCalculation) {
    assertThat(sentenceCalculation.homeDetentionCurfewEligibilityDate).isNotNull()
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate).isPositive()
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED]).isNotNull
  }

  private fun sentenceCalculation(sentence: StandardDeterminateSentence, numberOfDaysToSED: Int, numberOfDaysToDeterminateReleaseDate: Int, adjustments: Adjustments) =
    SentenceCalculation(
      sentence,
      numberOfDaysToSED,
      numberOfDaysToDeterminateReleaseDate.toDouble(),
      numberOfDaysToDeterminateReleaseDate,
      numberOfDaysToDeterminateReleaseDate,
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
    sentence: StandardDeterminateSentence,
  ) {
    calculator.calculateHdced(sentence, sentenceCalculation)
  }
}
