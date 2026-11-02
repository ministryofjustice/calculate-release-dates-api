package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ErsedConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AffectedBySdsLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AppliedAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ERSLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier.Companion.toLongReleaseDays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.hasSentencesBeforeAndAfter
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class ErsedCalculator(
  private val ersedConfiguration: ErsedConfiguration,
) {

  fun generateEarlyReleaseSchemeEligibilityDateBreakdown(sentence: CalculableSentence, currentSentenceGroup: List<CalculableSentence>, enabledLegislation: List<ERSLegislation>) {
    val sentenceCalculation = sentence.sentenceCalculation
    if (isSentenceEligible(sentence)) {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.ERSED] = if (ERSLegislation.ERS_ZERO in enabledLegislation) {
        calculateUsingERSZeroERS30AndERS50(sentence, sentenceCalculation, currentSentenceGroup)
      } else if (ERSLegislation.ERS30 in enabledLegislation) {
        calculateUsingBothERS30AndERS50(sentence, sentenceCalculation)
      } else {
        calculateUsingJustERS50(sentence, sentenceCalculation)
      }
    }
  }

  private fun calculateUsingERSZeroERS30AndERS50(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    currentSentenceGroup: List<CalculableSentence>,
  ): ReleaseDateCalculationBreakdown {
    val ers50Or30 = calculateUsingBothERS30AndERS50(sentence, sentenceCalculation)
    return if (ers50Or30.releaseDate.isBefore(ImportantDates.ERS_ZERO_COMMENCEMENT_DATE) || isExcludedFromErsZero(currentSentenceGroup)) {
      ers50Or30
    } else {
      // TODO post-commencement sentences should default to the sentence date
      ReleaseDateCalculationBreakdown(
        releaseDate = ImportantDates.ERS_ZERO_COMMENCEMENT_DATE,
        unadjustedDate = ImportantDates.ERS_ZERO_COMMENCEMENT_DATE,
        adjustedDays = 0,
        rules = setOf(CalculationRule.ERSED_ADJUSTED_TO_ERS_ZERO_COMMENCEMENT),
        appliedAdjustments = AppliedAdjustments(0),
        affectedBySdsLegislation = AffectedBySdsLegislation.from(sentenceCalculation.sentence),
      )
    }
  }

  private fun isExcludedFromErsZero(currentSentenceGroup: List<CalculableSentence>): Boolean = currentSentenceGroup.flatMap { it.sentenceParts() }.any { sentencePart ->
    when (sentencePart) {
      is SopcSentence -> true
      is ExtendedDeterminateSentence -> true
      is StandardDeterminateSentence -> sentencePart.releaseArrangements.sdsEarlyReleaseExclusions.any { it.ersZeroExclusion }
      else -> false
    }
  }

  private fun calculateUsingBothERS30AndERS50(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation): ReleaseDateCalculationBreakdown {
    val isConsideredPostErs30CommencementSentence = isSentencedOnOrAfterCommencement(sentence) || isConsecutiveSentenceWithSentencesBeforeAndAfterCommencement(sentence)
    val ers50Result = if (isConsideredPostErs30CommencementSentence) {
      null
    } else {
      calculate(
        Params(
          sentence = sentence,
          sentenceCalculation = sentenceCalculation,
          maxPeriodUnit = ersedConfiguration.ers50MaxPeriodUnit,
          maxPeriodAmount = ersedConfiguration.ers50MaxPeriodAmount,
          releaseMultiplier = ersedConfiguration.ers50ReleasePoint.value,
        ),
      )
    }
    val ers30Result = calculate(
      Params(
        sentence = sentence,
        sentenceCalculation = sentenceCalculation,
        maxPeriodUnit = ersedConfiguration.ers30MaxPeriodUnit,
        maxPeriodAmount = ersedConfiguration.ers30MaxPeriodAmount,
        releaseMultiplier = ersedConfiguration.ers30ReleasePoint.value,
      ),
    )

    return if (ers50Result != null && ers50Result.adjustedDateExcludingAwarded.isBefore(ImportantDates.ERS30_COMMENCEMENT_DATE)) {
      ers50Result.breakdown
    } else if (isConsideredPostErs30CommencementSentence) {
      val priorErsedSnapshot = sentenceCalculation.ersedSnapshotPriorToLatestSentenceBeingImposedConsecutively
      if (
        priorErsedSnapshot != null &&
        priorErsedSnapshot.breakdown.releaseDate.minusDays(priorErsedSnapshot.awardedDuringCustodyAtSnapshot).isAfter(ers30Result.adjustedDateExcludingAwarded) &&
        priorErsedSnapshot.breakdown.releaseDate.isBefore(priorErsedSnapshot.snapshotDate)
      ) {
        priorErsedSnapshot.breakdown
      } else {
        ers30Result.breakdown
      }
    } else if (ers30Result.adjustedDateExcludingAwarded.isAfterOrEqualTo(ImportantDates.ERS30_COMMENCEMENT_DATE)) {
      ers30Result.breakdown
    } else {
      val daysAwarded = sentenceCalculation.adjustments.awardedDuringCustody -
        sentenceCalculation.adjustments.unusedAdaDays -
        sentenceCalculation.adjustments.servedAdaDays
      ReleaseDateCalculationBreakdown(
        releaseDate = ImportantDates.ERS30_COMMENCEMENT_DATE.plusDays(daysAwarded),
        unadjustedDate = ImportantDates.ERS30_COMMENCEMENT_DATE,
        adjustedDays = daysAwarded,
        rules = setOf(CalculationRule.ERSED_ADJUSTED_TO_ERS30_COMMENCEMENT),
        appliedAdjustments = AppliedAdjustments.forInitialRelease(sentenceCalculation.adjustments),
        affectedBySdsLegislation = AffectedBySdsLegislation.from(sentenceCalculation.sentence),
      )
    }
  }

  private fun calculateUsingJustERS50(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation): ReleaseDateCalculationBreakdown = calculate(
    Params(
      sentence = sentence,
      sentenceCalculation = sentenceCalculation,
      maxPeriodUnit = ersedConfiguration.ers50MaxPeriodUnit,
      maxPeriodAmount = ersedConfiguration.ers50MaxPeriodAmount,
      releaseMultiplier = ersedConfiguration.ers50ReleasePoint.value,
    ),
  ).breakdown

  private fun isConsecutiveSentenceWithSentencesBeforeAndAfterCommencement(sentence: CalculableSentence): Boolean = (sentence is ConsecutiveSentence) && sentence.hasSentencesBeforeAndAfter(ImportantDates.ERS30_COMMENCEMENT_DATE)

  private fun isSentencedOnOrAfterCommencement(sentence: CalculableSentence): Boolean = sentence.sentencedAt.isAfterOrEqualTo(ImportantDates.ERS30_COMMENCEMENT_DATE)

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
    val releaseMultiplier: BigDecimal,
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
          appliedAdjustments = AppliedAdjustments.forInitialRelease(params.sentenceCalculation.adjustments),
          affectedBySdsLegislation = AffectedBySdsLegislation.from(params.sentenceCalculation.sentence),
        ),
        params.sentence.sentencedAt.plusDays(params.sentenceCalculation.adjustments.ualDuringCustody),
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

    val unadjustedEffectiveRelease =
      params.sentenceCalculation.unadjustedExtendedDeterminateParoleEligibilityDate
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
      appliedAdjustments = AppliedAdjustments.forInitialRelease(params.sentenceCalculation.adjustments),
      affectedBySdsLegislation = AffectedBySdsLegislation.from(params.sentenceCalculation.sentence),
    )

    val daysUntilRelease =
      ChronoUnit.DAYS.between(params.sentence.sentencedAt, unadjustedEffectiveRelease).plus(1).toInt()

    val unadjustedMinimumErsed = params.sentence.sentencedAt
      .plusDays(BigDecimal.valueOf(daysUntilRelease.toLong()).multiply(params.releaseMultiplier).toLongReleaseDays())

    val minimumEffectiveErsed = unadjustedMinimumErsed
      .plusDays(params.sentenceCalculation.adjustments.adjustmentsForInitialRelease())
      .coerceAtMost(effectiveRelease)

    val minimumEffectiveErsedBreakdown = ReleaseDateCalculationBreakdown(
      rules = setOf(CalculationRule.ERSED_MIN_EFFECTIVE_DATE),
      releaseDate = minimumEffectiveErsed,
      unadjustedDate = unadjustedMinimumErsed,
      adjustedDays = ChronoUnit.DAYS.between(unadjustedMinimumErsed, minimumEffectiveErsed),
      appliedAdjustments = AppliedAdjustments.forInitialRelease(params.sentenceCalculation.adjustments),
      affectedBySdsLegislation = AffectedBySdsLegislation.from(params.sentenceCalculation.sentence),
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
