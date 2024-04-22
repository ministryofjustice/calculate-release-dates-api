package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ErsedConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

@Service
class ErsedCalculator(val ersedConfiguration: ErsedConfiguration) {

  fun generateEarlyReleaseSchemeEligibilityDateBreakdown(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ) {
    val ersed = calculateErsed(sentence, sentenceCalculation)

    if (ersed != null) {
      if (ersed.releaseDate.isBefore(sentence.sentencedAt)) {
        sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.ERSED] =
          ReleaseDateCalculationBreakdown(
            releaseDate = sentence.sentencedAt,
            unadjustedDate = sentence.sentencedAt,
            rules = setOf(CalculationRule.ERSED_BEFORE_SENTENCE_DATE),
          )
      } else {
        sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.ERSED] = ersed
      }
    }
  }

  private fun calculateErsed(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ): ReleaseDateCalculationBreakdown? {
    if (!sentence.isRecall() && sentence.calulateErsed()) {
      return calculateErsedMinOrMax(sentence, sentenceCalculation)
    }
    return null
  }

  private fun calculateErsedMinOrMax(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ): ReleaseDateCalculationBreakdown {
    log.info("sentence: $sentence")
    log.info("sentenceCalculation: $sentenceCalculation")

    val effectiveRelease =
      sentenceCalculation.extendedDeterminateParoleEligibilityDate ?: sentenceCalculation.adjustedDeterminateReleaseDate

    log.info("effectiveRelease: $effectiveRelease")
    log.info("maxPeriod: $ersedConfiguration.maxPeriodDays")

    val maxEffectiveErsed = effectiveRelease.minusDays(ersedConfiguration.maxPeriodDays.toLong())
    val maxEffectiveErsedReleaseCalcBreakdown = ReleaseDateCalculationBreakdown(
      rules = setOf(CalculationRule.ERSED_MAX_PERIOD),
      releaseDate = maxEffectiveErsed,
      unadjustedDate = sentenceCalculation.unadjustedExtendedDeterminateParoleEligibilityDate
        ?: sentenceCalculation.unadjustedDeterminateReleaseDate,
      adjustedDays = ChronoUnit.DAYS.between(
        sentenceCalculation.unadjustedDeterminateReleaseDate,
        sentenceCalculation.adjustedDeterminateReleaseDate,
      ).toInt(),
      rulesWithExtraAdjustments = mapOf(
        CalculationRule.ERSED_MAX_PERIOD to AdjustmentDuration(-ersedConfiguration.maxPeriodDays, ChronoUnit.DAYS),
      ),
    )
    log.info("maxEffectiveErsedReleaseCalcBreakdown: $maxEffectiveErsedReleaseCalcBreakdown")

    val release = sentenceCalculation.unadjustedExtendedDeterminateParoleEligibilityDate
      ?: sentenceCalculation.unadjustedDeterminateReleaseDate

    val daysUntilRelease = ChronoUnit.DAYS.between(sentence.sentencedAt, release).plus(1).toInt()
    val daysToRelease = ceil(daysUntilRelease.toDouble() / 2).toLong()
    val unadjustedErsed =
      sentence.sentencedAt
        .plusDays(daysToRelease)

    log.info("days between: ${sentence.sentencedAt} -> $release = (daysUntilRelease): $daysUntilRelease")
    log.info("unadjustedErsed (divide by two): $unadjustedErsed ($daysToRelease)")
    log.info("Apply the following add:(added days) subtract(deducted days) add(awarded days): (${sentenceCalculation.calculatedTotalAddedDays.toLong()}) (${sentenceCalculation.calculatedTotalDeductedDays.toLong()}) (${sentenceCalculation.calculatedTotalAwardedDays.toLong()}) ")

    log.info("Explain the adjustments (deductions): ${sentenceCalculation.calculatedTotalDeductedDays}")

    val minimumEffectiveErsed = unadjustedErsed
      .plusDays(sentenceCalculation.calculatedTotalAddedDays.toLong())
      .minusDays(sentenceCalculation.calculatedTotalDeductedDays.toLong())
      .plusDays(sentenceCalculation.calculatedTotalAwardedDays.toLong())

    log.info("adjustedErsed (adjustments applied): $minimumEffectiveErsed")

    val minimumEffectiveErsedReleaseCalcBreakdown = ReleaseDateCalculationBreakdown(
      rules = setOf(CalculationRule.ERSED_MIN_EFFECTIVE_DATE),
      releaseDate = minimumEffectiveErsed,
      unadjustedDate = unadjustedErsed,
      adjustedDays = ChronoUnit.DAYS.between(unadjustedErsed, minimumEffectiveErsed).toInt(),
    )

    log.info("Minimum effective ERSED: $minimumEffectiveErsed, Maximum effective ERSED $maxEffectiveErsed")

    return if (minimumEffectiveErsed.isAfter(maxEffectiveErsed)) {
      log.info("using min: $minimumEffectiveErsedReleaseCalcBreakdown")
      minimumEffectiveErsedReleaseCalcBreakdown
    } else {
      log.info("using max: $maxEffectiveErsedReleaseCalcBreakdown")
      maxEffectiveErsedReleaseCalcBreakdown
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ErsedCalculator::class.java)
  }
}
