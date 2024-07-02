package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.HdcedConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
    return if (sentence is ConsecutiveSentence){
      sentence.orderedSentences.any { it.isSDSPlus }
    } else {
      sentence.isSDSPlus
    }
  }

  private data class Hdced4Params(
    val custodialPeriod: Double,
    val dateHdcAppliesFrom: LocalDate,
    val adjustedDays: Int,
  )

  fun calculateHdced4(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation) {
    val custodialPeriod = sentenceCalculation.numberOfDaysToDeterminateReleaseDateDouble

    val params = Hdced4Params(custodialPeriod, sentence.sentencedAt, getAllAdjustments(sentenceCalculation))

    // If adjustments make the CRD before sentence date plus 14 days (i.e. a large REMAND days)
    // then we don't need a HDCED date.
    if (adjustedReleasePointIsLessThanMinimumEligiblePeriod(sentenceCalculation, sentence) || unadjustedReleasePointIsLessThanMinimumCustodialPeriod(params.custodialPeriod)
      || sentence is ConsecutiveSentence) {
      sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate = null
      sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate = 0
      sentenceCalculation.breakdownByReleaseDateType.remove(ReleaseDateType.HDCED4PLUS)
    } else {
      if (params.custodialPeriod < hdcedConfiguration.custodialPeriodMidPointDays) {
        calculateHdcedUnderMidpoint(sentenceCalculation, sentence, params)
      } else {
        calculateHdcedOverMidpoint(sentenceCalculation, sentence)
      }
    }
  }

  private fun calculateHdcedUnderMidpoint(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    params: Hdced4Params,
  ) {
    val halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod = max(
      hdcedConfiguration.custodialPeriodBelowMidpointMinimumDeductionDays,
      ceil(params.custodialPeriod.div(HALF)).toLong(),
    )

    sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate = halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod.plus(params.adjustedDays)
    sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate =
      params.dateHdcAppliesFrom.plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate)

    if (sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc)
        .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!)
    ) {
      calculateHdcedMinimumCustodialPeriod(sentence, sentenceCalculation, CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT)
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
  ) {
    val adjustedDays = getAllAdjustments(sentenceCalculation)
    sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate =
      sentenceCalculation.numberOfDaysToDeterminateReleaseDate
        .minus(hdcedConfiguration.custodialPeriodAboveMidpointDeductionDays + 1) // Extra plus one because we use the numberOfDaysToDeterminateReleaseDate param and not the sentencedAt param
        .plus(adjustedDays)
    sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate = sentence.sentencedAt
      .plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate)

    if (sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc)
        .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!)
    ) {
      calculateHdcedMinimumCustodialPeriod(sentence, sentenceCalculation, CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD)
    } else {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD to AdjustmentDuration(-hdcedConfiguration.custodialPeriodAboveMidpointDeductionDays.toInt())),
          adjustedDays = adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!,
          unadjustedDate = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!.plusDays(
            hdcedConfiguration.custodialPeriodAboveMidpointDeductionDays,
          ),
        )
    }
  }

  private fun getAllAdjustments(sentenceCalculation: SentenceCalculation) = sentenceCalculation.calculatedTotalAddedDays
    .plus(sentenceCalculation.calculatedTotalAwardedDays)
    .minus(sentenceCalculation.calculatedTotalDeductedDays)
    .minus(sentenceCalculation.calculatedUnusedReleaseAda)

  private fun calculateHdcedMinimumCustodialPeriod(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    parentRule: CalculationRule,
  ) {
    sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate = sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc)
    sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate = hdcedConfiguration.minimumDaysOnHdc
    sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS] =
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD, parentRule),
        rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(hdcedConfiguration.minimumDaysOnHdc.toInt())),
        adjustedDays = 0,
        releaseDate = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!,
        unadjustedDate = sentence.sentencedAt,
      )
  }

  private fun adjustedReleasePointIsLessThanMinimumEligiblePeriod(sentenceCalculation: SentenceCalculation, sentence: CalculableSentence) =
    sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc))

  private fun unadjustedReleasePointIsLessThanMinimumCustodialPeriod(custodialPeriod: Double) = custodialPeriod < hdcedConfiguration.minimumCustodialPeriodDays

  private fun calculateNonSdsPlusCustodialPeriod(nonSdsPlusSentences: List<CalculableSentence>, sdsPlusNotionalCrd: LocalDate): Long {
    val nonSdsPlusWithMultiplier = nonSdsPlusSentences.groupBy { it.identificationTrack }.map { (track, sentences) -> sentences to releaseDateMultiplierLookup.multiplierFor(track) }
    var sdsPlusThenNonSdsPlusNotionalCrd = sdsPlusNotionalCrd
    nonSdsPlusWithMultiplier.forEach { (sentences, multiplier) ->
      val releaseStartDate = sdsPlusThenNonSdsPlusNotionalCrd.plusDays(1)
      val daysInThisCustodialDuration = sentenceAggregator.getDaysInGroup(releaseStartDate, sentences) { it.custodialDuration() }
      val daysToReleaseInThisGroup = ceil(daysInThisCustodialDuration.toDouble().times(multiplier)).toLong()
      sdsPlusThenNonSdsPlusNotionalCrd = releaseStartDate
        .plusDays(daysToReleaseInThisGroup)
        .minusDays(1)
    }
    val nonSdsPlusCustodialPeriod = ChronoUnit.DAYS.between(sdsPlusNotionalCrd, sdsPlusThenNonSdsPlusNotionalCrd)
    return nonSdsPlusCustodialPeriod
  }

  private fun calculateSdsPlusNotionalCRD(
    sentence: CalculableSentence,
    sdsPlusSentences: List<CalculableSentence>,
    deductionDays: Int,
  ): LocalDate = sentence.sentencedAt
    .plusDays(ceil(sentenceAggregator.getDaysInGroup(sentence.sentencedAt, sdsPlusSentences) { it.custodialDuration() } * releaseDateMultiplierLookup.multiplierFor(SentenceIdentificationTrack.SDS_PLUS_RELEASE)).toLong())
    .minusDays(deductionDays.toLong())

  companion object {
    private const val HALF = 2L
  }
}
