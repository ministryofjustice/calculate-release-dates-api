package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max

@Service
class ErsedCalculator(val ersedConfiguration: ErsedConfiguration) {
  @Configuration
  data class ErsedConfiguration(
    @Value("\${ersed.envelope.release.halfway.days}") val releaseAtHalfWayErsedDays: Long,
    @Value("\${ersed.envelope.release.two-thirds.days}") val releaseAtTwoThirdsErsedDays: Long,
    @Value("\${ersed.envelope.release.max-period.days}") val maxErsedPeriodDays: Long,
  )

  fun earlyReleaseSchemeEligibilityDateBreakdown(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ) {

    val ersed = calculateErsed(sentence, sentenceCalculation)

    if (ersed != null && ersed.releaseDate.isBefore(sentence.sentencedAt))
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.ERSED] =
        ReleaseDateCalculationBreakdown(
          releaseDate = sentence.sentencedAt,
          unadjustedDate = sentence.sentencedAt,
          rules = setOf(CalculationRule.ERSED_BEFORE_SENTENCE_DATE),
        ) else if (ersed !== null) sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.ERSED] = ersed
  }

  private fun calculateErsed(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ): ReleaseDateCalculationBreakdown? {
    if (sentenceCalculation.calculateErsed && !sentence.isRecall()) {
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
  ): ReleaseDateCalculationBreakdown? {
    val effectiveRelease =
      sentenceCalculation.extendedDeterminateParoleEligibilityDate ?: sentenceCalculation.adjustedDeterminateReleaseDate
    val maxEffectiveErsed = effectiveRelease.minusDays(SentenceCalculation.MAX_ERSED_PERIOD_DAYS.toLong())
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
          -SentenceCalculation.MAX_ERSED_PERIOD_DAYS,
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
    return if (days >= SentenceCalculation.RELEASE_AT_TWO_THIRDS_ERSED_DAYS) {
      val release = sentenceCalculation.extendedDeterminateParoleEligibilityDate ?: sentenceCalculation.releaseDate
      val ersed = release.minusDays(SentenceCalculation.MAX_ERSED_PERIOD_DAYS.toLong())
      ReleaseDateCalculationBreakdown(
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
            -SentenceCalculation.MAX_ERSED_PERIOD_DAYS,
            ChronoUnit.DAYS,
          ),
        ),
      )
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
    return if (days >= SentenceCalculation.RELEASE_AT_HALFWAY_ERSED_DAYS) {
      val release = sentenceCalculation.extendedDeterminateParoleEligibilityDate ?: sentenceCalculation.releaseDate
      val ersed = release.minusDays(SentenceCalculation.MAX_ERSED_PERIOD_DAYS.toLong())
      ReleaseDateCalculationBreakdown(
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
            -SentenceCalculation.MAX_ERSED_PERIOD_DAYS,
            ChronoUnit.DAYS,
          ),
        ),
      )
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
    // These numbers are the point at which the calculated ERSED would be greater than a year before the release.
    const val RELEASE_AT_HALFWAY_ERSED_DAYS = 2180
    const val RELEASE_AT_TWO_THIRDS_ERSED_DAYS = 1635
    const val MAX_ERSED_PERIOD_DAYS = 544
    private val log: Logger = LoggerFactory.getLogger(ErsedCalculator::class.java)
  }
}
