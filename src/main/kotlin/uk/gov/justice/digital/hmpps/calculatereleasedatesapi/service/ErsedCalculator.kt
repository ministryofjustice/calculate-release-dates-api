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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

@Service
class ErsedCalculator(private val ersedConfiguration: ErsedConfiguration) {

  fun generateEarlyReleaseSchemeEligibilityDateBreakdown(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ) {
    val ersed = calculateErsed(sentence, sentenceCalculation) ?: return

    val addedDays = sentenceCalculation.adjustments.ualDuringCustody + sentenceCalculation.adjustments.awardedDuringCustody
    val adjustedErsed = ersed.releaseDate.minusDays(addedDays)

    sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.ERSED] =
      if (adjustedErsed.isBefore(sentence.sentencedAt)) {
        ReleaseDateCalculationBreakdown(
          releaseDate = sentence.sentencedAt.plusDays(addedDays),
          unadjustedDate = sentence.sentencedAt,
          adjustedDays = addedDays,
          rules = setOf(CalculationRule.ERSED_BEFORE_SENTENCE_DATE),
        )
      } else {
        ersed
      }
  }

  private fun calculateErsed(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ): ReleaseDateCalculationBreakdown? = when {
    sentence.isRecall() -> null
    !sentence.calculateErsed() -> null
    !isEligibleUnderCJAAndLASPO(sentence) -> null
    else -> calculateErsedMinOrMax(sentence, sentenceCalculation)
  }

  private fun isEligibleUnderCJAAndLASPO(sentence: CalculableSentence): Boolean = sentence !is StandardDeterminateSentence || !sentence.isBeforeCJAAndLASPO()

  private fun calculateErsedMinOrMax(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ): ReleaseDateCalculationBreakdown {
    val isExtended = sentenceCalculation.extendedDeterminateParoleEligibilityDate != null
    val effectiveRelease = if (isExtended) {
      sentenceCalculation.extendedDeterminateParoleEligibilityDate!!
    } else {
      sentenceCalculation.adjustedDeterminateReleaseDate
    }

    val unadjustedEffectiveRelease = sentenceCalculation.unadjustedExtendedDeterminateParoleEligibilityDate
      ?: sentenceCalculation.unadjustedReleaseDate.unadjustedDeterminateReleaseDate

    val maxEffectiveErsed = effectiveRelease.minus(ersedConfiguration.maxPeriod)

    val (unit, amount) = when {
      ersedConfiguration.maxPeriod.years > 0 -> ChronoUnit.YEARS to -ersedConfiguration.maxPeriod.years.toLong()
      else -> ChronoUnit.DAYS to -ersedConfiguration.maxPeriod.days.toLong()
    }

    val maxEffectiveErsedBreakdown = ReleaseDateCalculationBreakdown(
      rules = setOf(CalculationRule.ERSED_MAX_PERIOD),
      releaseDate = maxEffectiveErsed,
      unadjustedDate = unadjustedEffectiveRelease,
      adjustedDays = ChronoUnit.DAYS.between(
        sentenceCalculation.unadjustedReleaseDate.unadjustedDeterminateReleaseDate,
        sentenceCalculation.adjustedDeterminateReleaseDate,
      ),
      rulesWithExtraAdjustments = mapOf(
        CalculationRule.ERSED_MAX_PERIOD to AdjustmentDuration(amount, unit),
      ),
    )

    val daysUntilRelease = ChronoUnit.DAYS.between(sentence.sentencedAt, unadjustedEffectiveRelease).plus(1).toInt()
    val unadjustedMinimumErsed = sentence.sentencedAt
      .plusDays(ceil(daysUntilRelease * ersedConfiguration.releasePoint).toLong())
    val minimumEffectiveErsed = unadjustedMinimumErsed
      .plusDays(sentenceCalculation.adjustments.adjustmentsForInitialRelease())

    val minimumEffectiveErsedBreakdown = ReleaseDateCalculationBreakdown(
      rules = setOf(CalculationRule.ERSED_MIN_EFFECTIVE_DATE),
      releaseDate = minimumEffectiveErsed,
      unadjustedDate = unadjustedMinimumErsed,
      adjustedDays = ChronoUnit.DAYS.between(unadjustedMinimumErsed, minimumEffectiveErsed),
    )

    return if (minimumEffectiveErsed.isAfter(maxEffectiveErsed)) {
      log.info("Using minimum effective ERSED ($minimumEffectiveErsed) as it exceeds max limit ($maxEffectiveErsed)")
      minimumEffectiveErsedBreakdown
    } else {
      log.info("Using maximum effective ERSED ($maxEffectiveErsed)")
      maxEffectiveErsedBreakdown
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ErsedCalculator::class.java)
  }
}
