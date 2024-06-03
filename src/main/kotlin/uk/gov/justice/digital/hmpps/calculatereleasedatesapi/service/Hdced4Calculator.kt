package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.Hdced4Configuration
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
import kotlin.math.ceil
import kotlin.math.max

@Service
class Hdced4Calculator(val hdced4Configuration: Hdced4Configuration, val hdcedConfiguration: HdcedConfiguration) {

  fun doesHdced4DateApply(sentence: CalculableSentence, offender: Offender): Boolean {
    return !offender.isActiveSexOffender && !sentence.isDto() && !sentence.isSDSPlus
  }

  private fun adjustedReleasePointIsLessThanMinimumEligibilePeriod(sentenceCalculation: SentenceCalculation, sentence: CalculableSentence) =
    sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc))

  private fun unadjustedReleasePointIsLessThanMinimumCustodialPeriod(custodialPeriod: Double) = custodialPeriod < hdcedConfiguration.minimumCustodialPeriodDays

  fun calculateHdced4(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation) {
    val custodialPeriod = sentenceCalculation.numberOfDaysToDeterminateReleaseDateDouble
    // If adjustments make the CRD before sentence date plus 14 days (i.e. a large REMAND days)
    // then we don't need a HDCED date.
    if (adjustedReleasePointIsLessThanMinimumEligibilePeriod(sentenceCalculation, sentence) || unadjustedReleasePointIsLessThanMinimumCustodialPeriod(custodialPeriod)) {
      sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate = null
      return
    }

    val adjustedDays = sentenceCalculation.calculatedTotalAddedDays
      .plus(sentenceCalculation.calculatedTotalAwardedDays)
      .minus(sentenceCalculation.calculatedTotalDeductedDays)
      .minus(sentenceCalculation.calculatedUnusedReleaseAda)

    if (sentence is ConsecutiveSentence && !sentence.isSDSPlus) {
      if (sentence.orderedSentences.any { it.isSDSPlus }) {
        val sdsPlusSentences = sentence.orderedSentences.filter { it.isSDSPlus }
        val nonSdsPlusSentences = sentence.orderedSentences.filter { !it.isSDSPlus }
        val sdsPlusLengthInDays = sdsPlusSentences.sumOf { it.getLengthInDays() }.toLong()
        val nonSdsPlusSentenceLengthInDays = nonSdsPlusSentences.sumOf { it.getLengthInDays() }.toLong()
        log.info("Total sds plus length in days sentences: {}", sdsPlusLengthInDays)
        val sdsPlusNotionalSled = sentence.sentencedAt.plusDays(sdsPlusLengthInDays)
        log.info("Sds plus notional sled: {}", sdsPlusNotionalSled)
        val sdsPlusNotionalCrd = sentence.sentencedAt.minusDays(1).plusDays(ceil(sdsPlusLengthInDays * (2.0 / 3.0)).toLong())
        log.info("Sds plus notional crd: {}", sdsPlusNotionalCrd)
        val lengthInDaysOfNonSdsPlusSentences = sentence.orderedSentences.filter { !it.isSDSPlus }.sumOf { it.totalDuration().getLengthInDays(sdsPlusNotionalCrd) }.toLong()
        log.info("Length in days of non sds plus sentences: {}", lengthInDaysOfNonSdsPlusSentences)

        val sdsNotionalSled = sdsPlusNotionalCrd.plusDays(lengthInDaysOfNonSdsPlusSentences)
        log.info("Sds notional sled: {}", sdsNotionalSled)
        if (nonSdsPlusSentenceLengthInDays < hdced4Configuration.envelopeMidPoint.value) {
          val hdcedDurationDays = max(hdcedConfiguration.custodialPeriodBelowMidpointMinimumDeductionDays, ceil(lengthInDaysOfNonSdsPlusSentences.toDouble().div(FOUR)).toLong())
          log.info("HDCED Duration days: {}", hdcedDurationDays)
          sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate =
            sdsPlusNotionalCrd.plusDays(1).plusDays(hdcedDurationDays).minusDays(sentence.sentenceCalculation.calculatedTotalDeductedDays.toLong()).plusDays(sentence.sentenceCalculation.calculatedTotalAddedDays.toLong())
        } else {
          sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate = sentenceCalculation.releaseDate.minusDays(hdcedConfiguration.custodialPeriodAboveMidpointDeductionDays)
        }
      } else {
        if (custodialPeriod < hdcedConfiguration.custodialPeriodMidPointDays) {
          calculateHdcedUnderMidpoint(sentenceCalculation, sentence, adjustedDays)
        } else {
          calculateHdcedOverMidpoint(sentence, adjustedDays, sentenceCalculation)
        }
      }

      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.CONSECUTIVE_SENTENCE_HDCED_CALCULATION),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(hdcedConfiguration.minimumDaysOnHdc.toInt())),
          adjustedDays = 0,
          releaseDate = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!,
          unadjustedDate = sentence.sentencedAt,
        )
    } else {
      if (custodialPeriod < hdcedConfiguration.custodialPeriodMidPointDays) {
        calculateHdcedUnderMidpoint(sentenceCalculation, sentence, adjustedDays)
      } else {
        calculateHdcedOverMidpoint(sentence, adjustedDays, sentenceCalculation)
      }
    }
  }

  private fun calculateHdcedUnderMidpoint(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    adjustedDays: Int,
  ) {
    val halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod = max(
      hdcedConfiguration.custodialPeriodBelowMidpointMinimumDeductionDays,
      ceil(sentenceCalculation.numberOfDaysToDeterminateReleaseDateDouble.div(HALF)).toLong(),
    )

    sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate = halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod.plus(adjustedDays)
    sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate =
      sentence.sentencedAt.plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate)

    if (sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc)
        .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!)
    ) {
      calculateHdcedMinimumCustodialPeriod(sentence, sentenceCalculation, CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT)
    } else {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT to AdjustmentDuration(halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod.toInt())),
          adjustedDays = adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!,
          unadjustedDate = sentence.sentencedAt,
        )
    }
  }

  private fun calculateHdcedOverMidpoint(
    sentence: CalculableSentence,
    adjustedDays: Int,
    sentenceCalculation: SentenceCalculation,
  ) {
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

  companion object {
    private const val FOUR = 4L
    private const val HALF = 2L
    private const val TWENTY_EIGHT = 28L
    private val log: Logger = LoggerFactory.getLogger(Hdced4Calculator::class.java)
  }
}
