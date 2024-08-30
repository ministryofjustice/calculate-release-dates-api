package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.HdcedConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

@Service
class Hdced4Calculator(
  val hdcedConfiguration: HdcedConfiguration,
  val sentenceAggregator: SentenceAggregator,
  val releaseDateMultiplierLookup: ReleasePointMultiplierLookup,
) {

  fun doesHdced4DateApply(sentence: CalculableSentence, offender: Offender): Boolean {
    return !offender.isActiveSexOffender && !sentence.isDto() && !hasSdsPlus(sentence)
  }

  fun hasSdsPlus(sentence: CalculableSentence): Boolean {
    return if (sentence is ConsecutiveSentence) {
      sentence.orderedSentences.any { it.isSDSPlus }
    } else {
      sentence.isSDSPlus
    }
  }

  private data class Hdced4Params(
    val custodialPeriod: Double,
    val dateHdcAppliesFrom: LocalDate,
    val deductedDays: Int,
    val addedDays: Int,
    val adjustedDays: Int = addedDays.minus(deductedDays),
  )

  fun calculateHdced4(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    extraDaysForSdsConsecutiveToBotus: Int = 0,
    useHistoricReleaseDate: Boolean,
  ) {
    val (custodialPeriod, custodialPeriodDouble) = determineCustodialPeriod(sentenceCalculation, !useHistoricReleaseDate)

    val params = Hdced4Params(custodialPeriodDouble, sentence.sentencedAt, getDeductedDays(sentenceCalculation), getAddedDays(sentenceCalculation, extraDaysForSdsConsecutiveToBotus))

    // If adjustments make the CRD before sentence date plus 14 days (i.e. a large REMAND days)
    // then we don't need a HDCED date.
    if (adjustedReleasePointIsLessThanMinimumEligiblePeriod(sentenceCalculation, sentence) || unadjustedReleasePointIsLessThanMinimumCustodialPeriod(params.custodialPeriod)) {
      sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate = null
      sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate = 0
      sentenceCalculation.breakdownByReleaseDateType.remove(ReleaseDateType.HDCED4PLUS)
    } else {
      if (params.custodialPeriod < hdcedConfiguration.custodialPeriodMidPointDays) {
        calculateHdcedUnderMidpoint(sentenceCalculation, sentence, params, custodialPeriodDouble)
      } else {
        calculateHdcedOverMidpoint(sentenceCalculation, sentence, params, custodialPeriod)
      }
    }
  }

  private fun calculateHdcedUnderMidpoint(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    params: Hdced4Params,
    custodialPeriod: Double,
  ) {
    val halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod = max(
      hdcedConfiguration.custodialPeriodBelowMidpointMinimumDeductionDays,
      ceil(custodialPeriod.div(HALF)).toLong(),
    )

    sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate = halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod.plus(params.adjustedDays)
    sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate =
      params.dateHdcAppliesFrom.plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate)

    if (isCalculatedHdcLessThanTheMinimumHDCPeriod(sentence, sentenceCalculation, params)) {
      calculateHdcedMinimumCustodialPeriod(sentence, sentenceCalculation, CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT, params)
    } else {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT to AdjustmentDuration(halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod.toInt())),
          adjustedDays = params.adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!,
          unadjustedDate = sentence.sentencedAt,
        )
    }
  }

  private fun calculateHdcedOverMidpoint(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    params: Hdced4Params,
    custodialPeriod: Int,
  ) {
    sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate =
      custodialPeriod
        .minus(hdcedConfiguration.custodialPeriodAboveMidpointDeductionDays + 1) // Extra plus one because we use the numberOfDaysToDeterminateReleaseDate param and not the sentencedAt param
        .plus(params.adjustedDays)
    sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate = sentence.sentencedAt
      .plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate)

    if (isCalculatedHdcLessThanTheMinimumHDCPeriod(sentence, sentenceCalculation, params)) {
      calculateHdcedMinimumCustodialPeriod(sentence, sentenceCalculation, CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD, params)
    } else {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD to AdjustmentDuration(-hdcedConfiguration.custodialPeriodAboveMidpointDeductionDays.toInt())),
          adjustedDays = params.adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!,
          unadjustedDate = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!.plusDays(
            hdcedConfiguration.custodialPeriodAboveMidpointDeductionDays,
          ),
        )
    }
  }

  private fun getAddedDays(sentenceCalculation: SentenceCalculation, extraDaysForSdsConsecutiveToBotus: Int) = sentenceCalculation.calculatedTotalAddedDays
    .plus(sentenceCalculation.calculatedTotalAwardedDays)
    .plus(extraDaysForSdsConsecutiveToBotus)
    .minus(sentenceCalculation.calculatedUnusedReleaseAda)

  private fun getDeductedDays(sentenceCalculation: SentenceCalculation) = sentenceCalculation.calculatedTotalDeductedDays

  private fun calculateHdcedMinimumCustodialPeriod(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    parentRule: CalculationRule,
    params: Hdced4Params,
  ) {
    sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate = sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc).plusDays(params.addedDays.toLong())
    sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate = hdcedConfiguration.minimumDaysOnHdc.plus(params.addedDays)
    sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS] =
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD, parentRule),
        rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(hdcedConfiguration.minimumDaysOnHdc.toInt())),
        adjustedDays = params.addedDays,
        releaseDate = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!,
        unadjustedDate = sentence.sentencedAt,
      )
  }

  private fun isCalculatedHdcLessThanTheMinimumHDCPeriod(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    params: Hdced4Params,
  ) =
    // Is the HDCED date BEFORE additional days are added less than the minimum.
    sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc)
      .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!.minusDays(params.addedDays.toLong()))

  private fun adjustedReleasePointIsLessThanMinimumEligiblePeriod(sentenceCalculation: SentenceCalculation, sentence: CalculableSentence) =
    sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc))

  private fun unadjustedReleasePointIsLessThanMinimumCustodialPeriod(custodialPeriod: Double) = custodialPeriod < hdcedConfiguration.minimumCustodialPeriodDays

  private fun determineCustodialPeriod(sentenceCalculation: SentenceCalculation, useCurrentRelease: Boolean): Pair<Int, Double> {
    return if (useCurrentRelease) {
      Pair(sentenceCalculation.numberOfDaysToDeterminateReleaseDate, sentenceCalculation.numberOfDaysToDeterminateReleaseDateDouble)
    } else {
      Pair(sentenceCalculation.numberOfDaysToHistoricDeterminateReleaseDate, sentenceCalculation.numberOfDaysToHistoricDeterminateReleaseDateDouble)
    }
  }

  companion object {
    private const val HALF = 2L
  }
}
