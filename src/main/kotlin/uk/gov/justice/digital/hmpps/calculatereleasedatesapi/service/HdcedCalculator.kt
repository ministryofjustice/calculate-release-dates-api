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

  fun calculateHdced(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation, offender: Offender) {
    val custodialPeriod = sentenceCalculation.numberOfDaysToDeterminateReleaseDateDouble
    if (isNotEligibleForHDC(offender, sentence, sentenceCalculation, custodialPeriod)) {
      sentenceCalculation.homeDetentionCurfewEligibilityDate = null
      sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = 0
      return
    }
    val adjustedDays = sentenceCalculation.calculatedTotalAddedDays
      .plus(sentenceCalculation.calculatedTotalAwardedDays)
      .minus(sentenceCalculation.calculatedTotalDeductedDays)
      .minus(sentenceCalculation.calculatedUnusedReleaseAda)

    if (custodialPeriod < hdcedConfiguration.custodialPeriodMidPointDays) {
      calculateHdcedUnderMidpoint(adjustedDays, sentenceCalculation, sentence.sentencedAt)
    } else {
      calculateHdcedOverMidpoint(sentence, adjustedDays, sentenceCalculation)
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
    adjustedDays: Int,
    sentenceCalculation: SentenceCalculation,
    sentenceDate: LocalDate,
  ) {
    val halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod = max(
      hdcedConfiguration.custodialPeriodBelowMidpointMinimumDeductionDays,
      ceil(sentenceCalculation.numberOfDaysToDeterminateReleaseDateDouble.div(HALF)).toLong(),
    )
    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod.plus(adjustedDays)
    sentenceCalculation.homeDetentionCurfewEligibilityDate =
      sentenceDate.plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate)

    if (sentenceDate.plusDays(hdcedConfiguration.minimumDaysOnHdc)
        .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfewEligibilityDate!!)
    ) {
      calculateHdcedMinimumCustodialPeriod(sentenceCalculation, CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT, sentenceDate)
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
    sentence: CalculableSentence,
    adjustedDays: Int,
    sentenceCalculation: SentenceCalculation,
  ) {
    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate =
      sentenceCalculation.numberOfDaysToDeterminateReleaseDate
        .minus(hdcedConfiguration.custodialPeriodAboveMidpointDeductionDays + 1) // Extra plus one because we use the numberOfDaysToDeterminateReleaseDate param and not the sentencedAt param
        .plus(adjustedDays)
    sentenceCalculation.homeDetentionCurfewEligibilityDate = sentence.sentencedAt
      .plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate)

    if (sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc)
        .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfewEligibilityDate!!)
    ) {
      calculateHdcedMinimumCustodialPeriod(sentenceCalculation, CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD, sentence.sentencedAt)
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
    parentRule: CalculationRule,
    sentenceDate: LocalDate,
  ) {
    sentenceCalculation.homeDetentionCurfewEligibilityDate = sentenceDate.plusDays(hdcedConfiguration.minimumDaysOnHdc)

    sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] =
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD, parentRule),
        rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(hdcedConfiguration.minimumDaysOnHdc.toInt())),
        adjustedDays = 0,
        releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
        unadjustedDate = sentenceDate,
      )
  }

  companion object {
    private const val HALF = 2L
    private const val MINIMUM_HDCED_PERIOD = 28L
  }
}
