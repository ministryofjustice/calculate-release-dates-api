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
      log.trace("HDCED Does not apply: Sex Offender")
      return true
    }
    return false
  }

  private fun isHDCEligibleSentence(sentence: CalculableSentence): Boolean {
    if (sentence.isDto()) {
      log.trace("HDCED Does not apply: DTO")
      return false
    }
    if (hasSdsPlus(sentence)) {
      log.trace("HDCED Does not apply: SDS+")
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

      // HDC365 reset too (copy of HDC functionlity above
      sentenceCalculation.homeDetentionCurfewEligibilityDateHDC365 = null
      sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDateHDC365 = 0
      sentenceCalculation.breakdownByReleaseDateType.remove(ReleaseDateType.HDCED365)
    } else {
      // NEED HDCED
      log.info("Calculating HDCED >> custodialPeriod " + params.custodialPeriod)
      if (params.custodialPeriod < hdcedConfiguration.custodialPeriodMidPointDays) {
        log.info("Calculating HDCED Under Midpoint")
        calculateHdcedUnderMidpoint(sentenceCalculation, sentence, params)
      } else {
        log.info("Calculating HDCED Over Midpoint")
        calculateHdcedOverMidpoint(sentenceCalculation, sentence, params)
      }

      if (params.custodialPeriod < hdcedConfiguration.custodialPeriodMidPointDaysHdc365) {
        log.info("Calculating HDCED365 Under Midpoint")
        calculateHdcedUnderMidpointHDC365(sentenceCalculation, sentence, params)
      } else {
        log.info("Calculating HDCED365 Over Midpoint")
        calculateHdcedOverMidpointHDC365(sentenceCalculation, sentence, params)
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

  // This is an exact copy of calculateHdcedUnderMidpoint - the only difference being it saves it against HDCED365 Release Date type
  // Duplicated the method rather than modified it with switches as the other method can just be deleted after the HDC365 commencement date
  private fun calculateHdcedUnderMidpointHDC365(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    params: HdcedParams,
  ) {
    val halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod = max(
      hdcedConfiguration.custodialPeriodBelowMidpointMinimumDeductionDays, // 28
      ceil(params.custodialPeriod.div(HALF)).toLong(),
    )

    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDateHDC365 =
      halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod.plus(params.adjustedDays)
    sentenceCalculation.homeDetentionCurfewEligibilityDateHDC365 =
      params.dateHdcAppliesFrom.plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDateHDC365)

    if (isCalculatedHdc365LessThanTheMinimumHDCPeriod(sentence, sentenceCalculation, params)) {
      calculateHdcedMinimumCustodialPeriodHDC365(
        sentence,
        sentenceCalculation,
        CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT,
        params,
      )
    } else {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED365] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT),
          rulesWithExtraAdjustments = mapOf(
            CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT to AdjustmentDuration(
              halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod,
            ),
          ),
          adjustedDays = params.adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDateHDC365!!,
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
    log.info(("HDCED numberOfDaysToHomeDetentionCurfewEligibilityDate: " + sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate))
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

  // This is an almost exact copy of calculateHdcedOverMidpoint method - the only differences being it saves it against HDCED365 Release Date type and uses the new config params (365 days instead of 180)
  // Duplicated the method rather than modified it with switches as the other method can just be deleted after the HDC365 commencement date
  private fun calculateHdcedOverMidpointHDC365(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    params: HdcedParams,
  ) {
    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDateHDC365 =
      sentenceCalculation.numberOfDaysToDeterminateReleaseDate
        .minus(hdcedConfiguration.custodialPeriodAboveMidpointDeductionDaysHdc365 + 1) // Changed for HDC365 Extra plus one because we use the numberOfDaysToDeterminateReleaseDate param and not the sentencedAt param
        .plus(params.adjustedDays)
    sentenceCalculation.homeDetentionCurfewEligibilityDateHDC365 = sentence.sentencedAt
      .plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDateHDC365)

    if (isCalculatedHdc365LessThanTheMinimumHDCPeriod(sentence, sentenceCalculation, params)) {
      // set to 14
      calculateHdcedMinimumCustodialPeriodHDC365(
        sentence,
        sentenceCalculation,
        CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD,
        params,
      )
    } else {
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED365] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD to AdjustmentDuration(-hdcedConfiguration.custodialPeriodAboveMidpointDeductionDaysHdc365)),
          adjustedDays = params.adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDateHDC365!!,
          unadjustedDate = sentenceCalculation.homeDetentionCurfewEligibilityDateHDC365!!.plusDays(
            hdcedConfiguration.custodialPeriodAboveMidpointDeductionDaysHdc365,
          ),
        )
    }
  }

  private fun getAddedDays(sentenceCalculation: SentenceCalculation) = sentenceCalculation.adjustments.ualDuringCustody +
    sentenceCalculation.adjustments.awardedDuringCustody -
    sentenceCalculation.adjustments.unusedAdaDays -
    sentenceCalculation.adjustments.servedAdaDays

  private fun getDeductedDays(sentenceCalculation: SentenceCalculation) =
    sentenceCalculation.adjustments.deductions

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

  // Exact copy of above calculateHdcedMinimumCustodialPeriod method, just sets the HDC365 version, this willreplace the original after commencement of HDC365
  private fun calculateHdcedMinimumCustodialPeriodHDC365(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    parentRule: CalculationRule,
    params: HdcedParams,
  ) {
    sentenceCalculation.homeDetentionCurfewEligibilityDateHDC365 =
      sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc).plusDays(
        params.addedDays,
      )
    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDateHDC365 =
      hdcedConfiguration.minimumDaysOnHdc.plus(params.addedDays)
    sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED365] =
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD, parentRule),
        rulesWithExtraAdjustments = mapOf(
          CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(
            hdcedConfiguration.minimumDaysOnHdc,
          ),
        ),
        adjustedDays = params.addedDays,
        releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDateHDC365!!,
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

  // Copy of isCalculatedHdcLessThanTheMinimumHDCPeriod for HDC365
  private fun isCalculatedHdc365LessThanTheMinimumHDCPeriod(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    params: HdcedParams,
  ) =
    // Is the HDCED date BEFORE additional days are added less than the minimum.
    sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc)
      .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfewEligibilityDateHDC365!!.minusDays(params.addedDays))

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
