package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max

@Service
class HdcedCalculator(val hdcedConfiguration: HdcedConfiguration) {
  @Configuration
  data class HdcedConfiguration(
    @Value("\${hdced.envelope.minimum.value}") val envelopeMinimum: Long,
    @Value("\${hdced.envelope.minimum.unit}")val envelopeMinimumUnit: ChronoUnit,
    @Value("\${hdced.envelope.maximum.value}")val envelopeMaximum: Long,
    @Value("\${hdced.envelope.maximum.unit}")val envelopeMaximumUnit: ChronoUnit,
    @Value("\${hdced.custodialDays.minimum}")val minimumCustodialPeriodDays: Long,
    @Value("\${hdced.envelope.midPoint.value}")val envelopeMidPoint: Long,
    @Value("\${hdced.envelope.midPoint.unit}")val envelopeMidPointUnit: ChronoUnit,
    @Value("\${hdced.deduction.days}")val deductionDays: Long
  )

  fun doesHdcedDateApply(sentence: CalculableSentence, offender: Offender, isMadeUpOfOnlyDtos: Boolean): Boolean {
    return sentence.durationIsGreaterThanOrEqualTo(hdcedConfiguration.envelopeMinimum, hdcedConfiguration.envelopeMinimumUnit) &&
      sentence.durationIsLessThan(hdcedConfiguration.envelopeMaximum, hdcedConfiguration.envelopeMaximumUnit) && !offender.isActiveSexOffender && !isMadeUpOfOnlyDtos
  }

  fun calculateHdced(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation) {
    // If adjustments make the CRD before sentence date plus 14 days (i.e. a large REMAND days)
    // then we don't need a HDCED date.
    if (sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(sentence.sentencedAt.plusDays(hdcedConfiguration.minimumCustodialPeriodDays))) {
      sentenceCalculation.homeDetentionCurfewEligibilityDate = null
      return
    }

    val adjustedDays = sentenceCalculation.calculatedTotalAddedDays
      .plus(sentenceCalculation.calculatedTotalAwardedDays)
      .minus(sentenceCalculation.calculatedTotalDeductedDays)
      .minus(sentenceCalculation.calculatedUnusedReleaseAda)

    // Any sentences < 12W or >= 4Y have been excluded already in the identification service (no HDCED)
    if (sentence.durationIsLessThan(hdcedConfiguration.envelopeMidPoint, hdcedConfiguration.envelopeMidPointUnit)) {
      calculateHdcedUnderMidpoint(sentenceCalculation, sentence, adjustedDays)
    } else {
      calculateHdcedOverMidpoint(sentence, adjustedDays, sentenceCalculation)
    }
  }

  private fun calculateHdcedUnderMidpoint(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    adjustedDays: Int
  ) {
    val twentyEightOrMore =
      max(TWENTY_EIGHT, ceil(sentenceCalculation.numberOfDaysToSentenceExpiryDate.toDouble().div(FOUR)).toLong())

    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = twentyEightOrMore.plus(adjustedDays)
    sentenceCalculation.homeDetentionCurfewEligibilityDate =
      sentence.sentencedAt.plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate)

    if (sentence.sentencedAt.plusDays(hdcedConfiguration.minimumCustodialPeriodDays)
      .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfewEligibilityDate!!)
    ) {
      calculateHdcedMinimumCustodialPeriod(sentence, sentenceCalculation, CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT)
    } else {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT to AdjustmentDuration(twentyEightOrMore.toInt())),
          adjustedDays = adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
          unadjustedDate = sentence.sentencedAt
        )
    }
  }

  private fun calculateHdcedOverMidpoint(
    sentence: CalculableSentence,
    adjustedDays: Int,
    sentenceCalculation: SentenceCalculation
  ) {
    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate =
      sentenceCalculation.numberOfDaysToDeterminateReleaseDate
        .minus(hdcedConfiguration.deductionDays + 1) // Extra plus one because we use the numberOfDaysToDeterminateReleaseDate param and not the sentencedAt param
        .plus(adjustedDays)
    sentenceCalculation.homeDetentionCurfewEligibilityDate = sentence.sentencedAt
      .plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate)

    if (sentence.sentencedAt.plusDays(hdcedConfiguration.minimumCustodialPeriodDays)
      .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfewEligibilityDate!!)
    ) {
      calculateHdcedMinimumCustodialPeriod(sentence, sentenceCalculation, CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD)
    } else {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD to AdjustmentDuration(-hdcedConfiguration.deductionDays.toInt())),
          adjustedDays = adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
          unadjustedDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!.plusDays(
            hdcedConfiguration.deductionDays
          )
        )
    }
  }

  private fun calculateHdcedMinimumCustodialPeriod(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    parentRule: CalculationRule
  ) {
    sentenceCalculation.homeDetentionCurfewEligibilityDate = sentence.sentencedAt.plusDays(hdcedConfiguration.minimumCustodialPeriodDays)

    sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] =
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD, parentRule),
        rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(hdcedConfiguration.minimumCustodialPeriodDays.toInt())),
        adjustedDays = 0,
        releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
        unadjustedDate = sentence.sentencedAt
      )
  }

  companion object {
    private const val FOUR = 4L
    private const val TWENTY_EIGHT = 28L
  }
}
