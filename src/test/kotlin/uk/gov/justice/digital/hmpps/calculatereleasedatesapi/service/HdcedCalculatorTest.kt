package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class HdcedCalculatorTest {

  private val config: HdcedConfiguration = hdcedConfigurationForTests()
  private val calculator = HdcedCalculator(config)

  @Test
  fun `shouldn't calculate a date for a sex offender`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.DAYS to 150L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt)
    val sentenceCalculation = sentenceCalculation(sentence, 150, 75, Adjustments())

    calc(sentenceCalculation, sentence, isActiveSexOffender = true)

    assertHasNoHDCED(sentenceCalculation)
  }

  @Test
  fun `shouldn't calculate a date when the sentence length is less than the minimum`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.WEEKS to 11L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt)
    val sentenceCalculation = sentenceCalculation(sentence, 77, 39, Adjustments())

    calc(sentenceCalculation, sentence)

    assertHasNoHDCED(sentenceCalculation)
  }

  @Test
  fun `shouldn't calculate a date when the sentence length is greater than the maximum`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.YEARS to 5L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt)
    val sentenceCalculation = sentenceCalculation(sentence, 1825, 913, Adjustments())

    calc(sentenceCalculation, sentence)

    assertHasNoHDCED(sentenceCalculation)
  }

  @Test
  fun `shouldn't calculate a date when the adjusted release date is less than the minimum custodial period`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.WEEKS to 20L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt)

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
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt)

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
    ) /* Shows hint as Sentence date plus HDCED adjustment (35) plus/minus regular adjustment (0) */
  }

  @Test
  fun `should calculate a date with 28 days rules if the sentence length is greater than the minimum and less than midpoint and quarter is less than 28`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.WEEKS to 16L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt)

    val sentenceCalculation = sentenceCalculation(sentence, 112, 56, Adjustments())

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
    ) /* Shows hint as Sentence date plus HDCED adjustment (35) plus/minus regular adjustment (0) */
  }

  @Test
  fun `should enforce minimum custodial period if sentence is greater than the minimum and less than midpoint but adjustments reduce to less than min custodial period`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.WEEKS to 20L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt)

    val minus25DayAdjustments = Adjustments(mutableMapOf(AdjustmentType.REMAND to mutableListOf(Adjustment(sentencedAt, 25))))
    val sentenceCalculation = sentenceCalculation(sentence, 140, 70, minus25DayAdjustments)

    calc(sentenceCalculation, sentence)

    assertThat(sentenceCalculation.homeDetentionCurfewEligibilityDate).isEqualTo(LocalDate.of(2020, 1, 15))
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate).isEqualTo(10L) /* 35 minus 25 for remand */
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
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt)

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
    ) /* Hint shown as adjusted CRD minus 179 days */
  }

  @Test
  fun `should calculate a date with minimum custodial period if the sentence length is greater than the midpoint and less than maximum`() {
    val sentencedAt = LocalDate.of(2020, 1, 1)
    val duration = Duration(mapOf(ChronoUnit.DAYS to 722L))
    val offence = Offence(LocalDate.of(2020, 1, 1))
    val sentence = StandardDeterminateSentence(offence, duration, sentencedAt)

    val adjustedLessThanMinCustodialPeriod = Adjustments(mutableMapOf(AdjustmentType.REMAND to mutableListOf(Adjustment(sentencedAt, 180))))
    val sentenceCalculation = sentenceCalculation(sentence, 722, 361, adjustedLessThanMinCustodialPeriod)

    calc(sentenceCalculation, sentence)

    assertThat(sentenceCalculation.homeDetentionCurfewEligibilityDate).isEqualTo(LocalDate.of(2020, 1, 15))
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate).isEqualTo(1L)
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
    assertThat(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate).isEqualTo(0)
    assertThat(sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED]).isNull()
  }

  private fun sentenceCalculation(sentence: StandardDeterminateSentence, numberOfDaysToSED: Int, numberOfDaysToDeterminateReleaseDate: Int, adjustments: Adjustments) =
    SentenceCalculation(
      sentence,
      numberOfDaysToSED,
      numberOfDaysToDeterminateReleaseDate.toDouble(),
      numberOfDaysToDeterminateReleaseDate,
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
    isActiveSexOffender: Boolean = false,
  ) {
    val offender = Offender("ABC123", LocalDate.of(1980, 1, 1), isActiveSexOffender = isActiveSexOffender)
    if (calculator.doesHdcedDateApply(sentence, offender)) {
      calculator.calculateHdced(sentence, sentenceCalculation)
    }
  }
}
