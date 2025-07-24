package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ErsedConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.collections.set
import kotlin.math.ceil

@Service
class ErsedCalculator(
  private val ersedConfiguration: ErsedConfiguration,
  private val featureToggles: FeatureToggles,
) {

  fun generateEarlyReleaseSchemeEligibilityDateBreakdown(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation) {
    if (isSentenceEligible(sentence)) {
      if (featureToggles.useERS30Calculation) {
        log.info("Including ERS30 and ERS50 in ERSED calculation")
        calculateUsingBothERS30AndERS50(sentence, sentenceCalculation)
      } else {
        log.info("Using only ERS50 in ERSED calculation")
        calculateUsingERS50Only(sentence, sentenceCalculation)
      }
    }
  }

  private fun calculateUsingBothERS30AndERS50(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation) {
    val ers50Result = calculate(
      Params(
        sentence = sentence,
        sentenceCalculation = sentenceCalculation,
        maxPeriodUnit = ersedConfiguration.ers50MaxPeriodUnit,
        maxPeriodAmount = ersedConfiguration.ers50MaxPeriodAmount,
        releasePoint = ersedConfiguration.ers50ReleasePoint,
      ),
    )
    val ers30Result = calculate(
      Params(
        sentence = sentence,
        sentenceCalculation = sentenceCalculation,
        maxPeriodUnit = ersedConfiguration.ers30MaxPeriodUnit,
        maxPeriodAmount = ersedConfiguration.ers30MaxPeriodAmount,
        releasePoint = ersedConfiguration.ers30ReleasePoint,
      ),
    )

    if (ers50Result.adjustedDateExcludingAwarded.isBefore(ImportantDates.ERS30_COMMENCEMENT_DATE)) {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.ERSED] = ers50Result.breakdown
    } else if (ers30Result.adjustedDateExcludingAwarded.isAfterOrEqualTo(ImportantDates.ERS30_COMMENCEMENT_DATE)) {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.ERSED] = ers30Result.breakdown
    } else {
      val daysToBeAddedExcludingUAL = sentenceCalculation.adjustments.awardedDuringCustody -
        sentenceCalculation.adjustments.unusedAdaDays -
        sentenceCalculation.adjustments.servedAdaDays
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.ERSED] = ReleaseDateCalculationBreakdown(
        releaseDate = ImportantDates.ERS30_COMMENCEMENT_DATE.plusDays(daysToBeAddedExcludingUAL),
        unadjustedDate = ImportantDates.ERS30_COMMENCEMENT_DATE,
        adjustedDays = daysToBeAddedExcludingUAL,
        rules = setOf(CalculationRule.ERSED_ADJUSTED_TO_ERS30_COMMENCEMENT),
      )
    }
  }

  private fun calculateUsingERS50Only(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation) {
    val params = Params(
      sentence = sentence,
      sentenceCalculation = sentenceCalculation,
      maxPeriodUnit = ersedConfiguration.ers50MaxPeriodUnit,
      maxPeriodAmount = ersedConfiguration.ers50MaxPeriodAmount,
      releasePoint = ersedConfiguration.ers50ReleasePoint,
    )
    sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.ERSED] = calculate(params).breakdown
  }

  private fun isSentenceEligible(sentence: CalculableSentence): Boolean = when {
    sentence.isRecall() -> false
    !sentence.calculateErsed() -> false
    !isEligibleUnderCJAAndLASPO(sentence) -> false
    else -> true
  }

  private fun isEligibleUnderCJAAndLASPO(sentence: CalculableSentence): Boolean = sentence !is StandardDeterminateSentence || !sentence.isBeforeCJAAndLASPO()

  private data class Params(
    val sentence: CalculableSentence,
    val sentenceCalculation: SentenceCalculation,
    val maxPeriodUnit: ChronoUnit,
    val maxPeriodAmount: Long,
    val releasePoint: Double,
  )

  private data class Result(val breakdown: ReleaseDateCalculationBreakdown, val adjustedDateExcludingAwarded: LocalDate)

  private fun calculate(params: Params): Result {
    val ersed = calculateErsedMinOrMax(params)

    val addedDays = params.sentenceCalculation.adjustments.ualDuringCustody + params.sentenceCalculation.adjustments.awardedDuringCustody
    val adjustedErsed = ersed.releaseDate.minusDays(addedDays)

    return if (adjustedErsed.isBefore(params.sentence.sentencedAt)) {
      Result(
        ReleaseDateCalculationBreakdown(
          releaseDate = params.sentence.sentencedAt.plusDays(addedDays),
          unadjustedDate = params.sentence.sentencedAt,
          adjustedDays = addedDays,
          rules = setOf(CalculationRule.ERSED_BEFORE_SENTENCE_DATE),
        ),
        adjustedErsed.plusDays(params.sentenceCalculation.adjustments.ualDuringCustody),
      )
    } else {
      Result(ersed, adjustedErsed.plusDays(params.sentenceCalculation.adjustments.ualDuringCustody))
    }
  }

  private fun calculateErsedMinOrMax(params: Params): ReleaseDateCalculationBreakdown {
    val isExtended = params.sentenceCalculation.extendedDeterminateParoleEligibilityDate != null
    val effectiveRelease = if (isExtended) {
      params.sentenceCalculation.extendedDeterminateParoleEligibilityDate!!
    } else {
      params.sentenceCalculation.adjustedDeterminateReleaseDate
    }

    val unadjustedEffectiveRelease = params.sentenceCalculation.unadjustedExtendedDeterminateParoleEligibilityDate
      ?: params.sentenceCalculation.unadjustedReleaseDate.unadjustedDeterminateReleaseDate

    val maxEffectiveErsed = effectiveRelease.minus(params.maxPeriodAmount, params.maxPeriodUnit)

    val maxEffectiveErsedBreakdown = ReleaseDateCalculationBreakdown(
      rules = setOf(CalculationRule.ERSED_MAX_PERIOD),
      releaseDate = maxEffectiveErsed,
      unadjustedDate = unadjustedEffectiveRelease,
      adjustedDays = ChronoUnit.DAYS.between(
        params.sentenceCalculation.unadjustedReleaseDate.unadjustedDeterminateReleaseDate,
        params.sentenceCalculation.adjustedDeterminateReleaseDate,
      ),
      rulesWithExtraAdjustments = mapOf(
        CalculationRule.ERSED_MAX_PERIOD to AdjustmentDuration(-params.maxPeriodAmount, params.maxPeriodUnit),
      ),
    )

    val daysUntilRelease = ChronoUnit.DAYS.between(params.sentence.sentencedAt, unadjustedEffectiveRelease).plus(1).toInt()
    val unadjustedMinimumErsed = params.sentence.sentencedAt
      .plusDays(ceil(daysUntilRelease * params.releasePoint).toLong())
    val minimumEffectiveErsed = unadjustedMinimumErsed
      .plusDays(params.sentenceCalculation.adjustments.adjustmentsForInitialRelease())

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
