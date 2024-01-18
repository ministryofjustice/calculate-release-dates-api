package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

@Service
class ErsedCalculator(val ersedConfiguration: ErsedConfiguration) {
  @Configuration
  data class ErsedConfiguration(
    @Value("\${ersed.envelope.release.halfway.days}") val releaseAtHalfWayErsedDays: Int,
    @Value("\${ersed.envelope.release.two-thirds.days}") val releaseAtTwoThirdsErsedDays: Int,
    @Value("\${ersed.envelope.release.max-period.days}") val maxErsedPeriodDays: Int,
  )

  fun earlyReleaseSchemeEligibilityDateBreakdown(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ) {
    val ersed = calculateErsed(sentence, sentenceCalculation)

    if (ersed != null && ersed.releaseDate.isBefore(sentence.sentencedAt)) {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.ERSED] =
        ReleaseDateCalculationBreakdown(
          releaseDate = sentence.sentencedAt,
          unadjustedDate = sentence.sentencedAt,
          rules = setOf(CalculationRule.ERSED_BEFORE_SENTENCE_DATE),
        )
    } else if (ersed !== null) sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.ERSED] = ersed
  }

  private fun calculateErsed(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ): ReleaseDateCalculationBreakdown? {
    if (!sentence.isRecall()) {
      if (sentence.calculateErsedFromHalfway()) {
        return calculateErsedFromHalfway(sentence, sentenceCalculation)
      }
      if (sentence.calculateErsedFromTwoThirds()) {
        return calculateErsedFromTwoThirds(sentence, sentenceCalculation)
      }
      if (sentence.calulateErsedMixed()) {
        return calculateErsedMixed(sentence, sentenceCalculation)
      }
    }
    return null
  }

  private fun calculateErsedMixed(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ): ReleaseDateCalculationBreakdown {
    val effectiveRelease =
      sentenceCalculation.extendedDeterminateParoleEligibilityDate ?: sentenceCalculation.adjustedDeterminateReleaseDate
    val maxEffectiveErsed = effectiveRelease.minusDays(ersedConfiguration.maxErsedPeriodDays.toLong())
    val maxEffectiveErsedReleaseCalcBreakdown = ReleaseDateCalculationBreakdown(
      rules = setOf(CalculationRule.ERSED_MAX_PERIOD),
      releaseDate = maxEffectiveErsed,
      unadjustedDate = effectiveRelease,
      adjustedDays = ChronoUnit.DAYS.between(
        sentenceCalculation.unadjustedDeterminateReleaseDate,
        sentenceCalculation.adjustedDeterminateReleaseDate,
      ).toInt(),
      rulesWithExtraAdjustments = mapOf(
        CalculationRule.ERSED_MAX_PERIOD to AdjustmentDuration(
          ersedConfiguration.maxErsedPeriodDays,
          ChronoUnit.DAYS,
        ),
      ),
    )
    val release = sentenceCalculation.unadjustedExtendedDeterminateParoleEligibilityDate
      ?: sentenceCalculation.unadjustedDeterminateReleaseDate

    val daysUntilRelease = ChronoUnit.DAYS.between(sentence.sentencedAt, release).plus(1).toInt()
    val unadjustedErsed =
      sentence.sentencedAt
        .plusDays(ceil(daysUntilRelease.toDouble() / 2).toLong())
    val minimumEffectiveErsed = unadjustedErsed
      .plusDays(sentenceCalculation.calculatedTotalAddedDays.toLong())
      .minusDays(sentenceCalculation.calculatedTotalDeductedDays.toLong())
      .plusDays(sentenceCalculation.calculatedTotalAwardedDays.toLong())
    val minimumEffectiveErsedReleaseCalcBreakdown = ReleaseDateCalculationBreakdown(
      rules = setOf(CalculationRule.ERSED_MIXED_TERMS),
      releaseDate = minimumEffectiveErsed,
      unadjustedDate = unadjustedErsed,
      adjustedDays = ChronoUnit.DAYS.between(unadjustedErsed, minimumEffectiveErsed).toInt(),
    )

    return if (minimumEffectiveErsed.isAfter(maxEffectiveErsed)) {
      minimumEffectiveErsedReleaseCalcBreakdown
    } else {
      maxEffectiveErsedReleaseCalcBreakdown
    }
  }

  private fun calculateErsedFromTwoThirds(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ): ReleaseDateCalculationBreakdown {
    val days = if (sentence is ConsecutiveSentence) {
      ConsecutiveSentenceAggregator(sentence.orderedSentences.map { it.custodialDuration() }).calculateDays(
        sentence.sentencedAt,
      )
    } else {
      val custodialDuration = sentence.custodialDuration()
      custodialDuration.getLengthInDays(sentence.sentencedAt)
    }
    return if (days >= ersedConfiguration.releaseAtTwoThirdsErsedDays) {
      calculateErsedBreakdownUsingMaxPeriod(sentenceCalculation)
    } else {
      val unadjustedErsed =
        sentence.sentencedAt
          .plusDays(ceil(days.toDouble() / 3).toLong())
      val ersed = unadjustedErsed
        .plusDays(sentenceCalculation.calculatedTotalAddedDays.toLong())
        .minusDays(sentenceCalculation.calculatedTotalDeductedDays.toLong())
        .plusDays(sentenceCalculation.calculatedTotalAwardedDays.toLong())
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.ERSED_TWO_THIRDS),
        releaseDate = ersed,
        unadjustedDate = unadjustedErsed,
        adjustedDays = ChronoUnit.DAYS.between(unadjustedErsed, ersed).toInt(),
      )
    }
  }

  private fun calculateErsedBreakdownUsingMaxPeriod(sentenceCalculation: SentenceCalculation): ReleaseDateCalculationBreakdown {
    val release = sentenceCalculation.extendedDeterminateParoleEligibilityDate ?: sentenceCalculation.releaseDate
    val ersed = release.minusDays(ersedConfiguration.maxErsedPeriodDays.toLong())
    return ReleaseDateCalculationBreakdown(
      rules = setOf(CalculationRule.ERSED_MAX_PERIOD),
      releaseDate = ersed,
      unadjustedDate = sentenceCalculation.unadjustedExtendedDeterminateParoleEligibilityDate
        ?: sentenceCalculation.unadjustedDeterminateReleaseDate,
      adjustedDays = ChronoUnit.DAYS.between(
        sentenceCalculation.unadjustedDeterminateReleaseDate,
        sentenceCalculation.adjustedDeterminateReleaseDate,
      ).toInt(),
      rulesWithExtraAdjustments = mapOf(
        CalculationRule.ERSED_MAX_PERIOD to AdjustmentDuration(
          -ersedConfiguration.maxErsedPeriodDays,
          ChronoUnit.DAYS,
        ),
      ),
    )
  }

  private fun calculateErsedFromHalfway(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ): ReleaseDateCalculationBreakdown {
    val days = if (sentence is ConsecutiveSentence) {
      ConsecutiveSentenceAggregator((sentence).orderedSentences.map { it.custodialDuration() }).calculateDays(
        sentence.sentencedAt,
      )
    } else {
      val custodialDuration = sentence.custodialDuration()
      custodialDuration.getLengthInDays(sentence.sentencedAt)
    }
    return if (days >= ersedConfiguration.releaseAtHalfWayErsedDays) {
      calculateErsedBreakdownUsingMaxPeriod(sentenceCalculation)
    } else {
      val unadjustedErsed =
        sentence.sentencedAt
          .plusDays(ceil(days.toDouble() / 4).toLong())
      val ersed = unadjustedErsed
        .plusDays(sentenceCalculation.calculatedTotalAddedDays.toLong())
        .minusDays(sentenceCalculation.calculatedTotalDeductedDays.toLong())
        .plusDays(sentenceCalculation.calculatedTotalAwardedDays.toLong())
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.ERSED_HALFWAY),
        releaseDate = ersed,
        unadjustedDate = unadjustedErsed,
        adjustedDays = ChronoUnit.DAYS.between(unadjustedErsed, ersed).toInt(),
      )
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ErsedCalculator::class.java)
  }
}
