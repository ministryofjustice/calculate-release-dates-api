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
    extraDaysForSdsConsecToBotus: Int = 0,
  ) {
    val custodialPeriod = sentenceCalculation.numberOfDaysToDeterminateReleaseDateDouble
    if (isNotEligibleForHDC(offender, sentence, sentenceCalculation, custodialPeriod)) {
      sentenceCalculation.homeDetentionCurfewEligibilityDate = null
      sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = 0
      return
    }
    val deductedDays = sentenceCalculation.calculatedTotalDeductedDays
    val addedDays = sentenceCalculation.calculatedTotalAddedDays
      .plus(sentenceCalculation.calculatedTotalAwardedDays)
      .plus(extraDaysForSdsConsecToBotus)
      .minus(sentenceCalculation.calculatedUnusedReleaseAda)

    if (custodialPeriod < hdcedConfiguration.custodialPeriodMidPointDays) {
      calculateHdcedUnderMidpoint(sentence.sentencedAt, deductedDays, addedDays, sentenceCalculation)
    } else {
      calculateHdcedOverMidpoint(sentence.sentencedAt, deductedDays, addedDays, sentenceCalculation)
    }
  }

  private fun isNotEligibleForHDC(offender: Offender, sentence: CalculableSentence, sentenceCalculation: SentenceCalculation, custodialPeriod: Double) =
    offender.isActiveSexOffender ||
      sentenceLengthIsAboveOrEqualToMaximum(sentence) ||
      adjustedReleasePointIsLessThanMinimumEligibilePeriod(sentenceCalculation, sentence) ||
      unadjustedReleasePointIsLessThanMinimumCustodialPeriod(custodialPeriod)

  private fun sentenceLengthIsAboveOrEqualToMaximum(sentence: CalculableSentence) = sentence.durationIsGreaterThanOrEqualTo(hdcedConfiguration.maximumSentenceLengthYears, ChronoUnit.YEARS)

  private fun unadjustedReleasePointIsLessThanMinimumCustodialPeriod(custodialPeriod: Double) = custodialPeriod < hdcedConfiguration.minimumCustodialPeriodDays

  private fun adjustedReleasePointIsLessThanMinimumEligibilePeriod(sentenceCalculation: SentenceCalculation, sentence: CalculableSentence) =
    sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc))

  private fun calculateHdcedUnderMidpoint(
    sentenceDate: LocalDate,
    deductedDays: Int,
    addedDays: Int,
    sentenceCalculation: SentenceCalculation,
  ) {
    val adjustedDays = addedDays.minus(deductedDays)
    val halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod = max(
      hdcedConfiguration.custodialPeriodBelowMidpointMinimumDeductionDays,
      ceil(sentenceCalculation.numberOfDaysToDeterminateReleaseDateDouble.div(HALF)).toLong(),
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
  ) {
    val adjustedDays = addedDays.minus(deductedDays)
    setHDCED(sentenceCalculation, sentenceDate) {
      sentenceCalculation.numberOfDaysToDeterminateReleaseDate
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
