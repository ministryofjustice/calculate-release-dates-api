package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.HdcedConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.HDCED_ADJUSTED_TO_365_COMMENCEMENT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.HDC_180
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
      sentence.orderedSentences.any { it.isSDSPlusEligibleSentenceTypeLengthAndOffence }
    } else {
      sentence.isSDSPlusEligibleSentenceTypeLengthAndOffence
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

      // Reset HDC365 fields which are used for interim calculation purposes too
      sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES] = null
      sentenceCalculation.noDaysToHdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES] = 0
      sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES] = null
      sentenceCalculation.noDaysToHdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES] = 0
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

      log.info("CALCULATING HDC-365")
      if (!featureToggles.hdc365) {
        setToPreHdc365Values(sentenceCalculation)
      } else {
        applyHdc365Rules(sentenceCalculation)
      }
    }
  }

  private fun applyHdc365Rules(sentenceCalculation: SentenceCalculation) {
    log.info(">> HDCED_PRE_365_RULES ${sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES]!!}")
    log.info(">> HDCED_POST_365_RULES ${sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!}")
    if (sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES]!!.isBefore(ImportantDates.HDC_365_COMMENCEMENT_DATE)) {
      setToPreHdc365Values(sentenceCalculation)
    } else if (sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!.isBefore(ImportantDates.HDC_365_COMMENCEMENT_DATE)) {
      addHdc365CommencementDateRule(sentenceCalculation)
      sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = sentenceCalculation.noDaysToHdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!
      sentenceCalculation.homeDetentionCurfewEligibilityDate = ImportantDates.HDC_365_COMMENCEMENT_DATE
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] = sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!
    } else {
      sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = sentenceCalculation.noDaysToHdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!
      sentenceCalculation.homeDetentionCurfewEligibilityDate = sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]
      sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] = sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!
    }
  }

  private fun setToPreHdc365Values(sentenceCalculation: SentenceCalculation) {
    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = sentenceCalculation.noDaysToHdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES]!!
    sentenceCalculation.homeDetentionCurfewEligibilityDate = sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES]
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

    sentenceCalculation.noDaysToHdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES] =
      halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod.plus(params.adjustedDays)
    sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES] =
      params.dateHdcAppliesFrom.plusDays(sentenceCalculation.noDaysToHdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES]!!)

    if (isCalculatedHdcLessThanTheMinimumHDCPeriod(sentence, sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES]!!, params)) {
      calculateHdcedMinimumCustodialPeriodUsingPreHdc365Rules(
        sentence,
        sentenceCalculation,
        CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT,
        params,
      )
    } else {
      sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES] =
        ReleaseDateCalculationBreakdown(
          rules = addHdc365Rule(setOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT)),
          rulesWithExtraAdjustments = mapOf(
            CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT to AdjustmentDuration(
              halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod,
            ),
          ),
          adjustedDays = params.adjustedDays,
          releaseDate = sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES]!!,
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

    sentenceCalculation.noDaysToHdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES] =
      halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod.plus(params.adjustedDays)
    sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES] =
      params.dateHdcAppliesFrom.plusDays(sentenceCalculation.noDaysToHdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!)

    if (isCalculatedHdcLessThanTheMinimumHDCPeriod(sentence, sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!, params)) {
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
          releaseDate = sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!,
          unadjustedDate = sentence.sentencedAt,
        )
    }
  }

  private fun calculateHdcedOverMidpointUsingPreHdc365Rules(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    params: HdcedParams,
  ) {
    sentenceCalculation.noDaysToHdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES] =
      sentenceCalculation.numberOfDaysToDeterminateReleaseDate
        .minus(hdcedConfiguration.custodialPeriodAboveMidpointDeductionDaysPreHdc365 + 1) // Extra plus one because we use the numberOfDaysToDeterminateReleaseDate param and not the sentencedAt param
        .plus(params.adjustedDays)
    sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES] =
      sentence.sentencedAt.plusDays(sentenceCalculation.noDaysToHdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES]!!)

    if (isCalculatedHdcLessThanTheMinimumHDCPeriod(sentence, sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES]!!, params)) {
      calculateHdcedMinimumCustodialPeriodUsingPreHdc365Rules(
        sentence,
        sentenceCalculation,
        CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD,
        params,
      )
    } else {
      sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES] =
        ReleaseDateCalculationBreakdown(
          rules = addHdc365Rule(setOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD)),
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD to AdjustmentDuration(-hdcedConfiguration.custodialPeriodAboveMidpointDeductionDaysPreHdc365)),
          adjustedDays = params.adjustedDays,
          releaseDate = sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES]!!,
          unadjustedDate = sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES]!!.plusDays(
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
    sentenceCalculation.noDaysToHdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES] =
      sentenceCalculation.numberOfDaysToDeterminateReleaseDate
        .minus(hdcedConfiguration.custodialPeriodAboveMidpointDeductionDaysPostHdc365 + 1) // Extra plus one because we use the numberOfDaysToDeterminateReleaseDate param and not the sentencedAt param
        .plus(params.adjustedDays)
    sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES] = sentence.sentencedAt
      .plusDays(sentenceCalculation.noDaysToHdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!)

    if (isCalculatedHdcLessThanTheMinimumHDCPeriod(sentence, sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!, params)) {
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
          releaseDate = sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!,
          unadjustedDate = sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!.plusDays(
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
    sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES] = sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc).plusDays(params.addedDays)
    sentenceCalculation.noDaysToHdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES] = hdcedConfiguration.minimumDaysOnHdc.plus(params.addedDays)
    sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES] =
      ReleaseDateCalculationBreakdown(
        rules = addHdc365Rule(setOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD, parentRule)),
        rulesWithExtraAdjustments = mapOf(
          CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(
            hdcedConfiguration.minimumDaysOnHdc,
          ),
        ),
        adjustedDays = params.addedDays,
        releaseDate = sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_PRE_365_RULES]!!,
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
    sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES] = sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc).plusDays(params.addedDays)
    sentenceCalculation.noDaysToHdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES] = hdcedConfiguration.minimumDaysOnHdc.plus(params.addedDays)
    sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_POST_365_RULES] =
      ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD, parentRule),
        rulesWithExtraAdjustments = mapOf(
          CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(
            hdcedConfiguration.minimumDaysOnHdc,
          ),
        ),
        adjustedDays = params.addedDays,
        releaseDate = sentenceCalculation.hdcedByCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!,
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

  private fun addHdc365Rule(baseRules: Set<CalculationRule>): Set<CalculationRule> =
    if (featureToggles.hdc365) {
      baseRules + HDC_180
    } else {
      baseRules
    }

  private fun addHdc365CommencementDateRule(sentenceCalculation: SentenceCalculation) {
    sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_POST_365_RULES] =
      sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!.copy(
        rules = sentenceCalculation.breakdownByInterimHdcCalcType[InterimHdcCalcType.HDCED_POST_365_RULES]!!.rules + HDCED_ADJUSTED_TO_365_COMMENCEMENT,
      )
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val HALF = 2L
  }
}
