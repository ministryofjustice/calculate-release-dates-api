package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
class HdcedCalculator(
  val hdcedConfiguration: HdcedConfiguration,
) {

  fun doesHdcedDateApply(sentence: CalculableSentence, offender: Offender): Boolean {
    if (isSexOffender(offender)) {
      return false
    }
    if (!isHDCEligibleSentence(sentence)) {
      return false
    }
    return true
  }

  private fun isSexOffender(offender: Offender): Boolean {
    if (offender.isActiveSexOffender) {
      log.info("HDCED Does not apply: Sex Offender")
      return true
    }
    return false
  }

  private fun isHDCEligibleSentence(sentence: CalculableSentence): Boolean {
    if (sentence.isDto()) {
      log.info("HDCED Does not apply: DTO")
      return false
    }
    if (hasSdsPlus(sentence)) {
      log.info("HDCED Does not apply: SDS+")
      return false
    }
    return true
  }

  fun hasSdsPlus(sentence: CalculableSentence): Boolean {
    return if (sentence is ConsecutiveSentence) {
      sentence.orderedSentences.any { it.isSDSPlus }
    } else {
      sentence.isSDSPlus
    }
  }

  private data class HdcedParams(
    val custodialPeriod: Double,
    val dateHdcAppliesFrom: LocalDate,
    val deductedDays: Long,
    val addedDays: Long,
    val adjustedDays: Long = addedDays.minus(deductedDays),
  )

  fun calculateHdced(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation) {
    val custodialPeriod = sentenceCalculation.numberOfDaysToDeterminateReleaseDateDouble

    val params = HdcedParams(
      custodialPeriod,
      sentence.sentencedAt,
      getDeductedDays(sentenceCalculation),
      getAddedDays(sentenceCalculation),
    )

    // If adjustments make the CRD before sentence date plus 14 days (i.e. a large REMAND days)
    // then we don't need a HDCED date.
    if (adjustedReleasePointIsLessThanMinimumEligiblePeriod(
        sentenceCalculation,
        sentence,
      ) || unadjustedReleasePointIsLessThanMinimumCustodialPeriod(params.custodialPeriod)
    ) {
      sentenceCalculation.homeDetentionCurfewEligibilityDate = null
      sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = 0
      sentenceCalculation.breakdownByReleaseDateType.remove(ReleaseDateType.HDCED)
    } else {
      if (params.custodialPeriod < hdcedConfiguration.custodialPeriodMidPointDays) {
        calculateHdcedUnderMidpoint(sentenceCalculation, sentence, params)
      } else {
        calculateHdcedOverMidpoint(sentenceCalculation, sentence, params)
      }
    }
  }

  private fun calculateHdcedUnderMidpoint(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    params: HdcedParams,
  ) {
    val halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod = max(
      hdcedConfiguration.custodialPeriodBelowMidpointMinimumDeductionDays,
      ceil(params.custodialPeriod.div(HALF)).toLong(),
    )

    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate =
      halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod.plus(params.adjustedDays)
    sentenceCalculation.homeDetentionCurfewEligibilityDate =
      params.dateHdcAppliesFrom.plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate)

    if (isCalculatedHdcLessThanTheMinimumHDCPeriod(sentence, sentenceCalculation, params)) {
      calculateHdcedMinimumCustodialPeriod(
        sentence,
        sentenceCalculation,
        CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT,
        params,
      )
    } else {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT),
          rulesWithExtraAdjustments = mapOf(
            CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT to AdjustmentDuration(
              halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod,
            ),
          ),
          adjustedDays = params.adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
          unadjustedDate = sentence.sentencedAt,
        )
    }
  }

  private fun calculateHdcedOverMidpoint(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    params: HdcedParams,
  ) {
    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate =
      sentenceCalculation.numberOfDaysToDeterminateReleaseDate
        .minus(hdcedConfiguration.custodialPeriodAboveMidpointDeductionDays + 1) // Extra plus one because we use the numberOfDaysToDeterminateReleaseDate param and not the sentencedAt param
        .plus(params.adjustedDays)
    sentenceCalculation.homeDetentionCurfewEligibilityDate = sentence.sentencedAt
      .plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate)

    if (isCalculatedHdcLessThanTheMinimumHDCPeriod(sentence, sentenceCalculation, params)) {
      calculateHdcedMinimumCustodialPeriod(
        sentence,
        sentenceCalculation,
        CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD,
        params,
      )
    } else {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD to AdjustmentDuration(-hdcedConfiguration.custodialPeriodAboveMidpointDeductionDays)),
          adjustedDays = params.adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
          unadjustedDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!.plusDays(
            hdcedConfiguration.custodialPeriodAboveMidpointDeductionDays,
          ),
        )
    }
  }

  private fun getAddedDays(sentenceCalculation: SentenceCalculation) = sentenceCalculation.calculatedTotalAddedDays
    .plus(sentenceCalculation.calculatedTotalAwardedDays)
    .minus(sentenceCalculation.unusedAdaDays)

  private fun getDeductedDays(sentenceCalculation: SentenceCalculation) =
    sentenceCalculation.calculatedTotalDeductedDays.toLong()

  private fun calculateHdcedMinimumCustodialPeriod(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    parentRule: CalculationRule,
    params: HdcedParams,
  ) {
    sentenceCalculation.homeDetentionCurfewEligibilityDate =
      sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc).plusDays(
        params.addedDays,
      )
    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate =
      hdcedConfiguration.minimumDaysOnHdc.plus(params.addedDays)
    sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] =
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD, parentRule),
        rulesWithExtraAdjustments = mapOf(
          CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(
            hdcedConfiguration.minimumDaysOnHdc,
          ),
        ),
        adjustedDays = params.addedDays,
        releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
        unadjustedDate = sentence.sentencedAt,
      )
  }

  private fun isCalculatedHdcLessThanTheMinimumHDCPeriod(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    params: HdcedParams,
  ) =
    // Is the HDCED date BEFORE additional days are added less than the minimum.
    sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc)
      .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfewEligibilityDate!!.minusDays(params.addedDays))

  private fun adjustedReleasePointIsLessThanMinimumEligiblePeriod(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
  ) =
    sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc))

  private fun unadjustedReleasePointIsLessThanMinimumCustodialPeriod(custodialPeriod: Double) =
    custodialPeriod < hdcedConfiguration.minimumCustodialPeriodDays

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val HALF = 2L
  }
}
