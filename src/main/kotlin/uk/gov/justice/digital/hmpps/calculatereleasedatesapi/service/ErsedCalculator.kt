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
        calculateUsingBothERS30AndERS50(sentence, sentenceCalculation).breakdown
      } else {
        calculateUsingJustERS50(sentence, sentenceCalculation).breakdown
      }
    }
  }

  private fun calculateUsingERSZeroERS30AndERS50(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    currentSentenceGroup: List<CalculableSentence>,
  ): ReleaseDateCalculationBreakdown {
    val ers50Or30 = calculateUsingBothERS30AndERS50(sentence, sentenceCalculation)
    val sentenceDate = sentence.sentencedAt
    val awarded = AppliedAdjustments.forErsed(sentence).awarded
    val isExcludedFromErsZero = isExcludedFromErsZero(currentSentenceGroup)
    val priorErsedSnapshot = sentenceCalculation.ersedSnapshotPriorToLatestSentenceBeingImposedConsecutively
    return if (!isExcludedFromErsZero && isConsecutiveSentenceWithSentencesBeforeAndAfterErsZeroCommencement(sentence) && priorErsedSnapshot != null && priorErsedSnapshot.breakdown.releaseDate.isBefore(ersedConfiguration.ersZeroCommencementDate)) {
      // if it's not excluded and it's a spanning consec sentence and the previous incarnation of the ERSED was before commencement then we keep it
      priorErsedSnapshot.breakdown
    } else if (ers50Or30.breakdown.releaseDate.isBefore(ersedConfiguration.ersZeroCommencementDate)) {
      // if we're before ERS0 while including ADAs then we can use the ERS30 as-is
      ers50Or30.breakdown
    } else if (isExcludedFromErsZero) {
      if (ers50Or30.adjustedDateExcludingAwarded.isAfter(ersedConfiguration.ersZeroCommencementDate)) {
        // if we're after ERS0 without including ADAs then we use the ERS30 with no ADAs applied
        ReleaseDateCalculationBreakdown(
          releaseDate = ers50Or30.adjustedDateExcludingAwarded,
          unadjustedDate = ers50Or30.breakdown.releaseDate,
          adjustedDays = ers50Or30.breakdown.adjustedDays.minus(awarded),
          rules = setOf(CalculationRule.ERSED_ADJUSTED_FOR_REMAINING_ADAS_POST_ERS_ZERO),
          appliedAdjustments = AppliedAdjustments(0),
          affectedBySdsLegislation = AffectedBySdsLegislation.from(sentenceCalculation.sentence),
          applicableErsLegislation = ers50Or30.breakdown.applicableErsLegislation,
        )
      } else {
        // but if we're only partially after ERS0 because of ADAs we adjust to the commencement date retaining the original legislation used
        ReleaseDateCalculationBreakdown(
          releaseDate = ersedConfiguration.ersZeroCommencementDate,
          unadjustedDate = ers50Or30.adjustedDateExcludingAwarded,
          adjustedDays = 0,
          rules = setOf(CalculationRule.ERSED_ADJUSTED_FOR_REMAINING_ADAS_POST_ERS_ZERO),
          appliedAdjustments = AppliedAdjustments(0),
          affectedBySdsLegislation = AffectedBySdsLegislation.from(sentenceCalculation.sentence),
          applicableErsLegislation = ers50Or30.breakdown.applicableErsLegislation,
        )
      }
    } else if (sentenceDate.isAfter(ersedConfiguration.ersZeroCommencementDate)) {
      ReleaseDateCalculationBreakdown(
        releaseDate = sentenceDate,
        unadjustedDate = sentenceDate,
        adjustedDays = 0,
        rules = setOf(CalculationRule.ERSED_ERS_ZERO),
        appliedAdjustments = AppliedAdjustments(0),
        affectedBySdsLegislation = AffectedBySdsLegislation.from(sentenceCalculation.sentence),
        applicableErsLegislation = ERSLegislation.ERS_ZERO,
      )
    } else {
      ReleaseDateCalculationBreakdown(
        releaseDate = ersedConfiguration.ersZeroCommencementDate,
        unadjustedDate = ersedConfiguration.ersZeroCommencementDate,
        adjustedDays = 0,
        rules = setOf(CalculationRule.ERSED_ADJUSTED_TO_ERS_ZERO_COMMENCEMENT),
        appliedAdjustments = AppliedAdjustments(0),
        affectedBySdsLegislation = AffectedBySdsLegislation.from(sentenceCalculation.sentence),
        applicableErsLegislation = ERSLegislation.ERS_ZERO,
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

  private fun calculateUsingBothERS30AndERS50(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation): Result {
    val isConsideredPostErs30CommencementSentence = isSentencedOnOrAfterErs30Commencement(sentence) || isConsecutiveSentenceWithSentencesBeforeAndAfterErs30Commencement(sentence)
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
          applicableLegislation = ERSLegislation.ERS50,
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
        applicableLegislation = ERSLegislation.ERS30,
      ),
    )

    val priorErsedSnapshot = sentenceCalculation.ersedSnapshotPriorToLatestSentenceBeingImposedConsecutively
    return if (ers50Result != null && ers50Result.adjustedDateExcludingAwarded.isBefore(ImportantDates.ERS30_COMMENCEMENT_DATE)) {
      ers50Result
    } else if (isConsideredPostErs30CommencementSentence) {
      if (
        priorErsedSnapshot != null &&
        priorErsedSnapshot.breakdown.releaseDate.minusDays(priorErsedSnapshot.awardedDuringCustodyAtSnapshot).isAfter(ers30Result.adjustedDateExcludingAwarded) &&
        priorErsedSnapshot.breakdown.releaseDate.isBefore(priorErsedSnapshot.snapshotDate)
      ) {
        Result(priorErsedSnapshot.breakdown, priorErsedSnapshot.breakdown.releaseDate.minusDays(priorErsedSnapshot.breakdown.appliedAdjustments?.awarded ?: 0))
      } else {
        ers30Result
      }
    } else if (ers30Result.adjustedDateExcludingAwarded.isAfterOrEqualTo(ImportantDates.ERS30_COMMENCEMENT_DATE)) {
      ers30Result
    } else {
      val daysAwarded = sentenceCalculation.adjustments.awardedDuringCustodyPriorToErsZero -
        sentenceCalculation.adjustments.unusedAdaDays -
        sentenceCalculation.adjustments.servedAdaDaysPriorToErsZero
      Result(
        ReleaseDateCalculationBreakdown(
          releaseDate = ImportantDates.ERS30_COMMENCEMENT_DATE.plusDays(daysAwarded),
          unadjustedDate = ImportantDates.ERS30_COMMENCEMENT_DATE,
          adjustedDays = daysAwarded,
          rules = setOf(CalculationRule.ERSED_ADJUSTED_TO_ERS30_COMMENCEMENT),
          appliedAdjustments = AppliedAdjustments.forErsed(sentenceCalculation.sentence),
          affectedBySdsLegislation = AffectedBySdsLegislation.from(sentenceCalculation.sentence),
          applicableErsLegislation = ERSLegislation.ERS30,
        ),
        ImportantDates.ERS30_COMMENCEMENT_DATE,
      )
    }
  }

  private fun calculateUsingJustERS50(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation): Result = calculate(
    Params(
      sentence = sentence,
      sentenceCalculation = sentenceCalculation,
      maxPeriodUnit = ersedConfiguration.ers50MaxPeriodUnit,
      maxPeriodAmount = ersedConfiguration.ers50MaxPeriodAmount,
      releaseMultiplier = ersedConfiguration.ers50ReleasePoint.value,
      applicableLegislation = ERSLegislation.ERS50,
    ),
  )

  private fun isConsecutiveSentenceWithSentencesBeforeAndAfterErs30Commencement(sentence: CalculableSentence): Boolean = (sentence is ConsecutiveSentence) && sentence.hasSentencesBeforeAndAfter(ImportantDates.ERS30_COMMENCEMENT_DATE)

  private fun isConsecutiveSentenceWithSentencesBeforeAndAfterErsZeroCommencement(sentence: CalculableSentence): Boolean = (sentence is ConsecutiveSentence) && sentence.hasSentencesBeforeAndAfter(ersedConfiguration.ersZeroCommencementDate)

  private fun isSentencedOnOrAfterErs30Commencement(sentence: CalculableSentence): Boolean = sentence.sentencedAt.isAfterOrEqualTo(ImportantDates.ERS30_COMMENCEMENT_DATE)

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
    val applicableLegislation: ERSLegislation,
  )

  private data class Result(val breakdown: ReleaseDateCalculationBreakdown, val adjustedDateExcludingAwarded: LocalDate)

  private fun calculate(params: Params): Result {
    val ersed = calculateErsedMinOrMax(params)

    val addedDays = params.sentenceCalculation.adjustments.ualDuringCustody + params.sentenceCalculation.adjustments.awardedDuringCustodyPriorToErsZero - params.sentenceCalculation.adjustments.servedAdaDaysPriorToErsZero
    val adjustedErsed = ersed.releaseDate.minusDays(addedDays)

    return if (adjustedErsed.isBefore(params.sentence.sentencedAt)) {
      Result(
        ReleaseDateCalculationBreakdown(
          releaseDate = params.sentence.sentencedAt.plusDays(addedDays),
          unadjustedDate = params.sentence.sentencedAt,
          adjustedDays = addedDays,
          rules = setOf(CalculationRule.ERSED_BEFORE_SENTENCE_DATE),
          appliedAdjustments = AppliedAdjustments.forErsed(params.sentence),
          affectedBySdsLegislation = AffectedBySdsLegislation.from(params.sentenceCalculation.sentence),
          applicableErsLegislation = params.applicableLegislation,
        ),
        params.sentence.sentencedAt.plusDays(params.sentenceCalculation.adjustments.ualDuringCustody),
      )
    } else {
      Result(ersed, adjustedErsed.plusDays(params.sentenceCalculation.adjustments.ualDuringCustody))
    }
  }

  private fun calculateErsedMinOrMax(params: Params): ReleaseDateCalculationBreakdown {
    val isExtended = params.sentenceCalculation.extendedDeterminateParoleEligibilityDate != null
    val adjustmentsForErsed = params.sentenceCalculation.adjustments.adjustmentsForErsed()
    val (unadjustedEffectiveRelease, effectiveRelease) = if (isExtended) {
      params.sentenceCalculation.unadjustedExtendedDeterminateParoleEligibilityDate!! to params.sentenceCalculation.unadjustedExtendedDeterminateParoleEligibilityDate!!.plusDays(adjustmentsForErsed)
    } else {
      params.sentenceCalculation.unadjustedDeterminateReleaseDate to params.sentenceCalculation.unadjustedDeterminateReleaseDate.plusDays(adjustmentsForErsed)
    }

    val maxEffectiveErsed = effectiveRelease.minus(params.maxPeriodAmount, params.maxPeriodUnit)

    val maxEffectiveErsedBreakdown = ReleaseDateCalculationBreakdown(
      rules = setOf(CalculationRule.ERSED_MAX_PERIOD),
      releaseDate = maxEffectiveErsed,
      unadjustedDate = unadjustedEffectiveRelease,
      adjustedDays = adjustmentsForErsed,
      rulesWithExtraAdjustments = mapOf(
        CalculationRule.ERSED_MAX_PERIOD to AdjustmentDuration(-params.maxPeriodAmount, params.maxPeriodUnit),
      ),
      appliedAdjustments = AppliedAdjustments.forErsed(params.sentence),
      affectedBySdsLegislation = AffectedBySdsLegislation.from(params.sentenceCalculation.sentence),
      applicableErsLegislation = params.applicableLegislation,
    )

    val daysUntilRelease =
      ChronoUnit.DAYS.between(params.sentence.sentencedAt, unadjustedEffectiveRelease).plus(1).toInt()

    val unadjustedMinimumErsed = params.sentence.sentencedAt
      .plusDays(BigDecimal.valueOf(daysUntilRelease.toLong()).multiply(params.releaseMultiplier).toLongReleaseDays())

    val minimumEffectiveErsed = unadjustedMinimumErsed
      .plusDays(adjustmentsForErsed)
      .coerceAtMost(effectiveRelease)

    val minimumEffectiveErsedBreakdown = ReleaseDateCalculationBreakdown(
      rules = setOf(CalculationRule.ERSED_MIN_EFFECTIVE_DATE),
      releaseDate = minimumEffectiveErsed,
      unadjustedDate = unadjustedMinimumErsed,
      adjustedDays = ChronoUnit.DAYS.between(unadjustedMinimumErsed, minimumEffectiveErsed),
      appliedAdjustments = AppliedAdjustments.forErsed(params.sentence),
      affectedBySdsLegislation = AffectedBySdsLegislation.from(params.sentenceCalculation.sentence),
      applicableErsLegislation = params.applicableLegislation,
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
