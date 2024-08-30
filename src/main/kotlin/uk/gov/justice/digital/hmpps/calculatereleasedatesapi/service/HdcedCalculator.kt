package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.HdcedConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max

@Service
class HdcedCalculator(val hdcedConfiguration: HdcedConfiguration) {

  fun calculateHdced(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    offender: Offender,
    extraDaysForSdsConsecutiveToBotus: Int = 0,
    useHistoricReleaseDate: Boolean = false,
  ) {
    val (custodialPeriod, custodialPeriodDouble) = determineCustodialPeriod(sentenceCalculation, !useHistoricReleaseDate)

    if (isNotEligibleForHDC(offender, sentence, sentenceCalculation, custodialPeriodDouble)) {
      resetHdcedCalculation(sentenceCalculation)
      return
    }

    val (deductedDays, addedDays) = calculateDaysAdjustments(sentenceCalculation, extraDaysForSdsConsecutiveToBotus)

    if (custodialPeriod < hdcedConfiguration.custodialPeriodMidPointDays) {
      calculateHdcedUnderMidpoint(sentence.sentencedAt, deductedDays, addedDays, sentenceCalculation, custodialPeriodDouble)
    } else {
      calculateHdcedOverMidpoint(sentence.sentencedAt, deductedDays, addedDays, sentenceCalculation, custodialPeriod)
    }
  }

  private fun determineCustodialPeriod(sentenceCalculation: SentenceCalculation, useCurrentRelease: Boolean): Pair<Int, Double> {
    return if (useCurrentRelease) {
      Pair(sentenceCalculation.numberOfDaysToDeterminateReleaseDate, sentenceCalculation.numberOfDaysToDeterminateReleaseDateDouble)
    } else {
      Pair(sentenceCalculation.numberOfDaysToHistoricDeterminateReleaseDate, sentenceCalculation.numberOfDaysToHistoricDeterminateReleaseDateDouble)
    }
  }

  private fun isNotEligibleForHDC(offender: Offender, sentence: CalculableSentence, sentenceCalculation: SentenceCalculation, custodialPeriod: Double): Boolean {
    return offender.isActiveSexOffender ||
      sentenceLengthIsAboveOrEqualToMaximum(sentence) ||
      adjustedReleasePointIsLessThanMinimumEligiblePeriod(sentenceCalculation, sentence) ||
      unadjustedReleasePointIsLessThanMinimumCustodialPeriod(custodialPeriod)
  }

  private fun sentenceLengthIsAboveOrEqualToMaximum(sentence: CalculableSentence): Boolean {
    return sentence.durationIsGreaterThanOrEqualTo(hdcedConfiguration.maximumSentenceLengthYears, ChronoUnit.YEARS)
  }

  private fun unadjustedReleasePointIsLessThanMinimumCustodialPeriod(custodialPeriod: Double): Boolean {
    return custodialPeriod < hdcedConfiguration.minimumCustodialPeriodDays
  }

  private fun adjustedReleasePointIsLessThanMinimumEligiblePeriod(sentenceCalculation: SentenceCalculation, sentence: CalculableSentence): Boolean {
    return sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc))
  }

  private fun resetHdcedCalculation(sentenceCalculation: SentenceCalculation) {
    sentenceCalculation.homeDetentionCurfewEligibilityDate = null
    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = 0
  }

  private fun calculateDaysAdjustments(sentenceCalculation: SentenceCalculation, extraDaysForSdsConsecutiveToBotus: Int): Pair<Int, Int> {
    val deductedDays = sentenceCalculation.calculatedTotalDeductedDays
    val addedDays = sentenceCalculation.calculatedTotalAddedDays
      .plus(sentenceCalculation.calculatedTotalAwardedDays)
      .plus(extraDaysForSdsConsecutiveToBotus)
      .minus(sentenceCalculation.calculatedUnusedReleaseAda)
    return Pair(deductedDays, addedDays)
  }

  private fun calculateHdcedUnderMidpoint(
    sentenceDate: LocalDate,
    deductedDays: Int,
    addedDays: Int,
    sentenceCalculation: SentenceCalculation,
    custodialPeriod: Double,
  ) {
    val adjustedDays = addedDays.minus(deductedDays)
    val halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod = max(
      hdcedConfiguration.custodialPeriodBelowMidpointMinimumDeductionDays,
      ceil(custodialPeriod.div(HALF)).toLong(),
    )
    setHDCED(sentenceCalculation, sentenceDate) { halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod.plus(adjustedDays) }

    if (isCalculatedHdcLessThanTheMinimumHDCPeriod(sentenceDate, sentenceCalculation, addedDays)) {
      calculateHdcedMinimumCustodialPeriod(sentenceCalculation, sentenceDate, CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT, addedDays)
    } else {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT to AdjustmentDuration(halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod.toInt())),
          adjustedDays = adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
          unadjustedDate = sentenceDate,
        )
    }
  }

  private fun calculateHdcedOverMidpoint(
    sentenceDate: LocalDate,
    deductedDays: Int,
    addedDays: Int,
    sentenceCalculation: SentenceCalculation,
    custodialPeriod: Int,
  ) {
    val adjustedDays = addedDays.minus(deductedDays)
    setHDCED(sentenceCalculation, sentenceDate) {
      custodialPeriod
        .minus(hdcedConfiguration.custodialPeriodAboveMidpointDeductionDays + 1) // Extra plus one because we use the numberOfDaysToDeterminateReleaseDate param and not the sentencedAt param
        .plus(adjustedDays)
    }
    if (isCalculatedHdcLessThanTheMinimumHDCPeriod(sentenceDate, sentenceCalculation, addedDays)) {
      calculateHdcedMinimumCustodialPeriod(sentenceCalculation, sentenceDate, CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD, addedDays)
    } else {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD to AdjustmentDuration(-hdcedConfiguration.custodialPeriodAboveMidpointDeductionDays.toInt())),
          adjustedDays = adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
          unadjustedDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!.plusDays(
            hdcedConfiguration.custodialPeriodAboveMidpointDeductionDays,
          ),
        )
    }
  }

  private fun calculateHdcedMinimumCustodialPeriod(
    sentenceCalculation: SentenceCalculation,
    sentenceDate: LocalDate,
    parentRule: CalculationRule,
    addedDays: Int,
  ) {
    setHDCED(sentenceCalculation, sentenceDate) { hdcedConfiguration.minimumDaysOnHdc.plus(addedDays) }
    sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] =
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD, parentRule),
        rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(hdcedConfiguration.minimumDaysOnHdc.toInt())),
        adjustedDays = addedDays,
        releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
        unadjustedDate = sentenceDate,
      )
  }

  private fun setHDCED(
    sentenceCalculation: SentenceCalculation,
    sentenceDate: LocalDate,
    provideNumberOfDaysOnHDC: () -> Long,
  ) {
    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = provideNumberOfDaysOnHDC()
    sentenceCalculation.homeDetentionCurfewEligibilityDate = sentenceDate.plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate)
  }

  private fun isCalculatedHdcLessThanTheMinimumHDCPeriod(sentenceDate: LocalDate, sentenceCalculation: SentenceCalculation, addedDays: Int) =
    // Is the HDCED date BEFORE additional days are added less than the minimum.
    sentenceDate.plusDays(hdcedConfiguration.minimumDaysOnHdc)
      .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfewEligibilityDate!!.minusDays(addedDays.toLong()))

  companion object {
    private const val HALF = 2L
  }
}
