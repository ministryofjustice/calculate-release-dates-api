package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max

@Service
class Hdced4Calculator(val hdcedConfiguration: Hdced4Configuration) {
  @Configuration
  data class Hdced4Configuration(
    @Value("\${hdced.envelope.minimum.value}") val envelopeMinimum: Long,
    @Value("\${hdced.envelope.minimum.unit}") val envelopeMinimumUnit: ChronoUnit,
    @Value("\${hdced.custodialDays.minimum}") val minimumCustodialPeriodDays: Long,
    @Value("\${hdced.envelope.midPoint.value}") val envelopeMidPoint: Long,
    @Value("\${hdced.envelope.midPoint.unit}") val envelopeMidPointUnit: ChronoUnit,
    @Value("\${hdced.deduction.days}") val deductionDays: Long,
  )

  fun doesHdced4DateApply(sentence: CalculableSentence, offender: Offender, isMadeUpOfOnlyDtos: Boolean): Boolean {
    return sentence.durationIsGreaterThanOrEqualTo(hdcedConfiguration.envelopeMinimum, hdcedConfiguration.envelopeMinimumUnit) &&
      !offender.isActiveSexOffender && !isMadeUpOfOnlyDtos && ((sentence !is ConsecutiveSentence && !sentence.offence.isPcscSdsPlus) || (sentence is ConsecutiveSentence && sentence.isMadeUpOfSdsAndSdsPlusSentences()))
  }

  fun calculateHdced4(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation) {
    // If adjustments make the CRD before sentence date plus 14 days (i.e. a large REMAND days)
    // then we don't need a HDCED date.
    if (sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(sentence.sentencedAt.plusDays(hdcedConfiguration.minimumCustodialPeriodDays))) {
      sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate = null
      return
    }

    val adjustedDays = sentenceCalculation.calculatedTotalAddedDays
      .plus(sentenceCalculation.calculatedTotalAwardedDays)
      .minus(sentenceCalculation.calculatedTotalDeductedDays)
      .minus(sentenceCalculation.calculatedUnusedReleaseAda)

    // Now that we have sentences > 4 years there are rules around how Consecutive sentences containing SDS+ are calculated
    if (sentence is ConsecutiveSentence && sentence.orderedSentences.any { it.isSdsPlus() } && nonSdsPlusGreaterThanMinimum(sentence) && nonSdsLessThanMidpoint(sentence) && !lastSentenceIsSdsPlus(sentence)) {
      val latestNcrd = sentence.orderedSentences.filter { it.offence.isPcscSdsPlus || it.offence.isPcscSds }.maxBy { it.sentenceCalculation.releaseDate }
      val nonSdsPlusSentences = sentence.orderedSentences.filter { !it.offence.isPcscSdsPlus || !it.offence.isPcscSds }
      sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate =
        latestNcrd.sentenceCalculation.releaseDate.plusDays(nonSdsPlusSentences.sumOf { it.getLengthInDays() + nonSdsPlusSentences.size }.toLong() / 4)!!.plusDays(1)
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.CONSECUTIVE_SENTENCE_HDCED_MINIMUM_CUSTODIAL_PERIOD),
          rulesWithExtraAdjustments = mapOf(CalculationRule.CONSECUTIVE_SENTENCE_HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(hdcedConfiguration.minimumCustodialPeriodDays.toInt())),
          adjustedDays = 0,
          releaseDate = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!,
          unadjustedDate = sentence.sentencedAt,
        )
    } else if (sentence is ConsecutiveSentence && sentence.orderedSentences.any { it.isSdsPlus() } && nonSdsGreaterThanMidpoint(sentence) && lastSentenceIsSdsPlus(sentence)) {
      val sdsPlusSentences = sentence.orderedSentences.filter { it.offence.isPcscSdsPlus || it.offence.isPcscSds }
      val sdsPlusLengthInDays = sdsPlusSentences.sumOf { it.getLengthInDays() }.toLong()
      log.info("Total sds plus length in days sentences: {}", sdsPlusLengthInDays)
      val sdsPlusNotionalSled = sentence.sentencedAt.plusDays(sdsPlusLengthInDays)
      log.info("Sds plus notional sled: {}", sdsPlusNotionalSled)
      val sdsPlusNotionalCrd = sentence.sentencedAt.minusDays(1).plusDays(ceil(sdsPlusLengthInDays * (2.0 / 3.0)).toLong())
      log.info("Sds plus notional crd: {}", sdsPlusNotionalCrd)
      val lengthInDaysOfNonSdsPlusSentences = sentence.orderedSentences.filter { !it.offence.isPcscSdsPlus }.filter { !it.offence.isPcscSds }.sumOf { it.totalDuration().getLengthInDays(sdsPlusNotionalCrd) }.toLong()
      log.info("Length in days of non sds plus sentences: {}", lengthInDaysOfNonSdsPlusSentences)

      val sdsNotionalSled = sdsPlusNotionalCrd.plusDays(lengthInDaysOfNonSdsPlusSentences)
      log.info("Sds notional sled: {}", sdsNotionalSled)
      val quarterSentenceLength = ceil(lengthInDaysOfNonSdsPlusSentences / 4.0).toLong()
      log.info("Quarter sentence length: {}", quarterSentenceLength)

      sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate = sdsPlusNotionalCrd.plusDays(quarterSentenceLength).minusDays(sentence.sentenceCalculation.calculatedTotalDeductedDays.toLong())
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.CONSECUTIVE_SENTENCE_HDCED_MINIMUM_CUSTODIAL_PERIOD_LAST_SENTENCE_SDS),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(hdcedConfiguration.minimumCustodialPeriodDays.toInt())),
          adjustedDays = 0,
          releaseDate = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!,
          unadjustedDate = sentence.sentencedAt,
        )
    } else {
      if (sentence.durationIsLessThan(hdcedConfiguration.envelopeMidPoint, hdcedConfiguration.envelopeMidPointUnit)) {
        calculateHdcedUnderMidpoint(sentenceCalculation, sentence, adjustedDays)
      } else {
        calculateHdcedOverMidpoint(sentence, adjustedDays, sentenceCalculation)
      }
    }
  }

  private fun lastSentenceIsSdsPlus(sentence: ConsecutiveSentence) =
    sentence.orderedSentences[sentence.orderedSentences.size - 1].offence.isPcscSdsPlus || sentence.orderedSentences[sentence.orderedSentences.size - 1].offence.isPcscSds
  private fun nonSdsLessThanMidpoint(sentence: ConsecutiveSentence) =
    sentence.orderedSentences.filter { !it.offence.isPcscSdsPlus || !it.offence.isPcscSds }.sumOf { it.getLengthInDays() } < hdcedConfiguration.envelopeMidPoint

  private fun nonSdsGreaterThanMidpoint(sentence: ConsecutiveSentence) =
    sentence.orderedSentences.filter { !it.offence.isPcscSdsPlus || !it.offence.isPcscSds }.sumOf { it.getLengthInDays() } > hdcedConfiguration.envelopeMidPoint

  private fun nonSdsPlusGreaterThanMinimum(sentence: ConsecutiveSentence) =
    sentence.orderedSentences.filter { !it.offence.isPcscSdsPlus || !it.offence.isPcscSds }.sumOf { it.getLengthInDays() } >= 84

  private fun calculateHdcedUnderMidpoint(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    adjustedDays: Int,
  ) {
    val twentyEightOrMore =
      max(TWENTY_EIGHT, ceil(sentenceCalculation.numberOfDaysToSentenceExpiryDate.toDouble().div(FOUR)).toLong())

    sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate = twentyEightOrMore.plus(adjustedDays)
    sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate =
      sentence.sentencedAt.plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate)

    if (sentence.sentencedAt.plusDays(hdcedConfiguration.minimumCustodialPeriodDays)
      .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!)
    ) {
      calculateHdcedMinimumCustodialPeriod(sentence, sentenceCalculation, CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT)
    } else {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT to AdjustmentDuration(twentyEightOrMore.toInt())),
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
        .minus(hdcedConfiguration.deductionDays + 1) // Extra plus one because we use the numberOfDaysToDeterminateReleaseDate param and not the sentencedAt param
        .plus(adjustedDays)
    sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate = sentence.sentencedAt
      .plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfew4PlusEligibilityDate)

    if (sentence.sentencedAt.plusDays(hdcedConfiguration.minimumCustodialPeriodDays)
      .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!)
    ) {
      calculateHdcedMinimumCustodialPeriod(sentence, sentenceCalculation, CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD)
    } else {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD to AdjustmentDuration(-hdcedConfiguration.deductionDays.toInt())),
          adjustedDays = adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!,
          unadjustedDate = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!.plusDays(
            hdcedConfiguration.deductionDays,
          ),
        )
    }
  }

  private fun calculateHdcedMinimumCustodialPeriod(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    parentRule: CalculationRule,
  ) {
    sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate = sentence.sentencedAt.plusDays(hdcedConfiguration.minimumCustodialPeriodDays)

    sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED4PLUS] =
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD, parentRule),
        rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(hdcedConfiguration.minimumCustodialPeriodDays.toInt())),
        adjustedDays = 0,
        releaseDate = sentenceCalculation.homeDetentionCurfew4PlusEligibilityDate!!,
        unadjustedDate = sentence.sentencedAt,
      )
  }

  companion object {
    private const val FOUR = 4L
    private const val TWENTY_EIGHT = 28L
    private val log: Logger = LoggerFactory.getLogger(Hdced4Calculator::class.java)
  }
}
