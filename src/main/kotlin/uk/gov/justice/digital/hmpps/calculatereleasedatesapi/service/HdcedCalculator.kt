package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.HdcedConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.InterimHdcCalcType
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
  val featureToggles: FeatureToggles,
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

      // Reset HDC365 fields too (eventually after HDC365 commencement the above can be refactored)
      sentenceCalculation.hdcedUsingPreHdc365Rules = null
      sentenceCalculation.noDaysToHdcedUsingPreHdc365Rules = 0
      sentenceCalculation.hdcedUsingPostHdc365Rules = null
      sentenceCalculation.noDaysToHdcedUsingPostHdc365Rules = 0
      sentenceCalculation.breakdownByInterimHdcCalcType = mutableMapOf()
    } else {
      if (params.custodialPeriod < hdcedConfiguration.custodialPeriodMidPointDaysPreHdc365) {
        calculateHdcedUnderMidpointUsingPreHdc365Rules(sentenceCalculation, sentence, params)
      } else {
        calculateHdcedOverMidpointUsingPreHdc365Rules(sentenceCalculation, sentence, params)
      }

      if (params.custodialPeriod < hdcedConfiguration.custodialPeriodMidPointDaysPostHdc365) {
        calculateHdcedUnderMidpointUsingPostHdc365Rules(sentenceCalculation, sentence, params)
      } else {
        calculateHdcedOverMidpointUsingPostHDC365Rules(sentenceCalculation, sentence, params)
      }

      if (!featureToggles.hdc365) {
        setToPreHdc365Values(sentenceCalculation)
      } else {
        applyHdc365Rules(sentenceCalculation)
      }
    }
  }

  private fun applyHdc365Rules(sentenceCalculation: SentenceCalculation) {
    if (sentenceCalculation.hdcedUsingPreHdc365Rules!!.isBefore(ImportantDates.HDC_365_COMMENCEMENT_DATE)) {
      setToPreHdc365Values(sentenceCalculation)
    } else if (sentenceCalculation.hdcedUsingPostHdc365Rules!!.isBefore(ImportantDates.HDC_365_COMMENCEMENT_DATE)) {
      sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = sentenceCalculation.noDaysToHdcedUsingPostHdc365Rules
      sentenceCalculation.homeDetentionCurfewEligibilityDate = ImportantDates.HDC_365_COMMENCEMENT_DATE
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] = sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!
    } else {
      sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = sentenceCalculation.noDaysToHdcedUsingPostHdc365Rules
      sentenceCalculation.homeDetentionCurfewEligibilityDate = sentenceCalculation.hdcedUsingPostHdc365Rules
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] = sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!
    }
  }

  private fun setToPreHdc365Values(sentenceCalculation: SentenceCalculation) {
    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = sentenceCalculation.noDaysToHdcedUsingPreHdc365Rules
    sentenceCalculation.homeDetentionCurfewEligibilityDate = sentenceCalculation.hdcedUsingPreHdc365Rules
    sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] = sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES]!!
  }

  private fun calculateHdcedUnderMidpointUsingPreHdc365Rules(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    params: HdcedParams,
  ) {
    val halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod = max(
      hdcedConfiguration.custodialPeriodBelowMidpointMinimumDeductionDays,
      ceil(params.custodialPeriod.div(HALF)).toLong(),
    )

    sentenceCalculation.noDaysToHdcedUsingPreHdc365Rules =
      halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod.plus(params.adjustedDays)
    sentenceCalculation.hdcedUsingPreHdc365Rules =
      params.dateHdcAppliesFrom.plusDays(sentenceCalculation.noDaysToHdcedUsingPreHdc365Rules)

    if (isCalculatedHdcLessThanTheMinimumHDCPeriod(sentence, sentenceCalculation.hdcedUsingPreHdc365Rules!!, params)) {
      calculateHdcedMinimumCustodialPeriodUsingPreHdc365Rules(
        sentence,
        sentenceCalculation,
        CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT,
        params,
      )
    } else {
      sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT),
          rulesWithExtraAdjustments = mapOf(
            CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT to AdjustmentDuration(
              halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod,
            ),
          ),
          adjustedDays = params.adjustedDays,
          releaseDate = sentenceCalculation.hdcedUsingPreHdc365Rules!!,
          unadjustedDate = sentence.sentencedAt,
        )
    }
  }

  // This is a copy of calculateHdcedUnderMidpointUsingPreHDC365Rules - the difference being it saves against the new Post HDC-365 variant variables and associated config
  // TODO potential to combine the methods into one, will look into separately
  private fun calculateHdcedUnderMidpointUsingPostHdc365Rules(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    params: HdcedParams,
  ) {
    val halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod = max(
      hdcedConfiguration.custodialPeriodBelowMidpointMinimumDeductionDays,
      ceil(params.custodialPeriod.div(HALF)).toLong(),
    )

    sentenceCalculation.noDaysToHdcedUsingPostHdc365Rules =
      halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod.plus(params.adjustedDays)
    sentenceCalculation.hdcedUsingPostHdc365Rules =
      params.dateHdcAppliesFrom.plusDays(sentenceCalculation.noDaysToHdcedUsingPostHdc365Rules)

    if (isCalculatedHdcLessThanTheMinimumHDCPeriod(sentence, sentenceCalculation.hdcedUsingPostHdc365Rules!!, params)) {
      calculateHdcedMinimumCustodialUsingPostHdc365Rules(
        sentence,
        sentenceCalculation,
        CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT,
        params,
      )
    } else {
      sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_POST_365_RULES] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT),
          rulesWithExtraAdjustments = mapOf(
            CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT to AdjustmentDuration(
              halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod,
            ),
          ),
          adjustedDays = params.adjustedDays,
          releaseDate = sentenceCalculation.hdcedUsingPostHdc365Rules!!,
          unadjustedDate = sentence.sentencedAt,
        )
    }
  }

  private fun calculateHdcedOverMidpointUsingPreHdc365Rules(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    params: HdcedParams,
  ) {
    sentenceCalculation.noDaysToHdcedUsingPreHdc365Rules =
      sentenceCalculation.numberOfDaysToDeterminateReleaseDate
        .minus(hdcedConfiguration.custodialPeriodAboveMidpointDeductionDaysPreHdc365 + 1) // Extra plus one because we use the numberOfDaysToDeterminateReleaseDate param and not the sentencedAt param
        .plus(params.adjustedDays)
    sentenceCalculation.hdcedUsingPreHdc365Rules = sentence.sentencedAt
      .plusDays(sentenceCalculation.noDaysToHdcedUsingPreHdc365Rules)

    if (isCalculatedHdcLessThanTheMinimumHDCPeriod(sentence, sentenceCalculation.hdcedUsingPreHdc365Rules!!, params)) {
      calculateHdcedMinimumCustodialPeriodUsingPreHdc365Rules(
        sentence,
        sentenceCalculation,
        CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD,
        params,
      )
    } else {
      sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD to AdjustmentDuration(-hdcedConfiguration.custodialPeriodAboveMidpointDeductionDaysPreHdc365)),
          adjustedDays = params.adjustedDays,
          releaseDate = sentenceCalculation.hdcedUsingPreHdc365Rules!!,
          unadjustedDate = sentenceCalculation.hdcedUsingPreHdc365Rules!!.plusDays(
            hdcedConfiguration.custodialPeriodAboveMidpointDeductionDaysPreHdc365,
          ),
        )
    }
  }

  // This is a copy of calculateHdcedOverMidpointUsingPreHdc365Rules - the difference being it saves against the new Post HDC-365 variant variables and associated config
  // TODO potential to combine the methods into one, will look into separately
  private fun calculateHdcedOverMidpointUsingPostHDC365Rules(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    params: HdcedParams,
  ) {
    sentenceCalculation.noDaysToHdcedUsingPostHdc365Rules =
      sentenceCalculation.numberOfDaysToDeterminateReleaseDate
        .minus(hdcedConfiguration.custodialPeriodAboveMidpointDeductionDaysPostHdc365 + 1) // Extra plus one because we use the numberOfDaysToDeterminateReleaseDate param and not the sentencedAt param
        .plus(params.adjustedDays)
    sentenceCalculation.hdcedUsingPostHdc365Rules = sentence.sentencedAt
      .plusDays(sentenceCalculation.noDaysToHdcedUsingPostHdc365Rules)

    if (isCalculatedHdcLessThanTheMinimumHDCPeriod(sentence, sentenceCalculation.hdcedUsingPostHdc365Rules!!, params)) {
      calculateHdcedMinimumCustodialUsingPostHdc365Rules(
        sentence,
        sentenceCalculation,
        CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD,
        params,
      )
    } else {
      sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_POST_365_RULES] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD to AdjustmentDuration(-hdcedConfiguration.custodialPeriodAboveMidpointDeductionDaysPostHdc365)),
          adjustedDays = params.adjustedDays,
          releaseDate = sentenceCalculation.hdcedUsingPostHdc365Rules!!,
          unadjustedDate = sentenceCalculation.hdcedUsingPostHdc365Rules!!.plusDays(
            hdcedConfiguration.custodialPeriodAboveMidpointDeductionDaysPostHdc365,
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

  private fun calculateHdcedMinimumCustodialPeriodUsingPreHdc365Rules(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    parentRule: CalculationRule,
    params: HdcedParams,
  ) {
    sentenceCalculation.hdcedUsingPreHdc365Rules = sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc).plusDays(params.addedDays)
    sentenceCalculation.noDaysToHdcedUsingPreHdc365Rules = hdcedConfiguration.minimumDaysOnHdc.plus(params.addedDays)
    sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES] =
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD, parentRule),
        rulesWithExtraAdjustments = mapOf(
          CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(
            hdcedConfiguration.minimumDaysOnHdc,
          ),
        ),
        adjustedDays = params.addedDays,
        releaseDate = sentenceCalculation.hdcedUsingPreHdc365Rules!!,
        unadjustedDate = sentence.sentencedAt,
      )
  }

  // This is a copy of calculateHdcedMinimumCustodialPeriodUsingPreHdc365Rules - the difference being it saves against the new Post HDC-365 variant variables and associated config
  // TODO potential to combine the methods into one, will look into separately
  private fun calculateHdcedMinimumCustodialUsingPostHdc365Rules(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    parentRule: CalculationRule,
    params: HdcedParams,
  ) {
    sentenceCalculation.hdcedUsingPostHdc365Rules = sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc).plusDays(params.addedDays)
    sentenceCalculation.noDaysToHdcedUsingPostHdc365Rules = hdcedConfiguration.minimumDaysOnHdc.plus(params.addedDays)
    sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_POST_365_RULES] =
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD, parentRule),
        rulesWithExtraAdjustments = mapOf(
          CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(
            hdcedConfiguration.minimumDaysOnHdc,
          ),
        ),
        adjustedDays = params.addedDays,
        releaseDate = sentenceCalculation.hdcedUsingPostHdc365Rules!!,
        unadjustedDate = sentence.sentencedAt,
      )
  }

  private fun isCalculatedHdcLessThanTheMinimumHDCPeriod(
    sentence: CalculableSentence,
    hdced: LocalDate,
    params: HdcedParams,
  ) =
    // Is the HDCED date BEFORE additional days are added less than the minimum.
    sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc).isAfterOrEqualTo(hdced.minusDays(params.addedDays))

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
