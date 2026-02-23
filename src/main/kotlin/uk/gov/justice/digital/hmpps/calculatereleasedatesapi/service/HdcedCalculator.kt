package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.HdcedConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.HDCED_ADJUSTED_TO_365_COMMENCEMENT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.HDC_180
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.HDC_365_COMMENCEMENT_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier.Companion.toLongReleaseDays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.hasSentencesBeforeAndAfter
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.math.BigDecimal
import java.time.LocalDate
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

  fun hasSdsPlus(sentence: CalculableSentence): Boolean = sentence.sentenceParts().any { it.isSDSPlusEligibleSentenceTypeLengthAndOffence }

  private data class HdcedCalculationInput(
    val custodialPeriodDecimal: BigDecimal,
    val custodialPeriod: Int,
    val dateHdcAppliesFrom: LocalDate,
    val deductedDays: Long,
    val ualToAdd: Long,
    val otherAdditions: Long,
    val custodialPeriodMidPointDays: Long,
    val custodialPeriodAboveMidpointDeductionDays: Long,
    val sentence: CalculableSentence,
    val additionalRulesForBreakdown: Set<CalculationRule>,
  ) {
    fun totalAdjustmentForBreakdown() = ualToAdd.plus(otherAdditions).minus(deductedDays)
  }

  private data class HdcedCalculationResult(
    val input: HdcedCalculationInput,
    val hdcedWithOnlyUalAdded: LocalDate,
    val adjustedHdced: LocalDate,
    val numberOfDaysToAdjustedHdced: Long,
    val breakdown: ReleaseDateCalculationBreakdown,
  )

  fun calculateHdced(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation) {
    val custodialPeriod = sentenceCalculation.numberOfDaysToDeterminateReleaseDateDecimal

    // If adjustments make the CRD before sentence date plus 14 days (i.e. a large REMAND days)
    // then we don't need a HDCED date.
    if (adjustedReleasePointIsLessThanMinimumEligiblePeriod(sentenceCalculation, sentence) ||
      unadjustedReleasePointIsLessThanMinimumCustodialPeriod(custodialPeriod)
    ) {
      noHdced(sentenceCalculation)
    } else {
      calculateHdced(sentenceCalculation, sentence)
    }
  }

  private fun calculateHdced(sentenceCalculation: SentenceCalculation, sentence: CalculableSentence) {
    val custodialPeriodDecimal = sentenceCalculation.numberOfDaysToDeterminateReleaseDateDecimal
    val custodialPeriod = sentenceCalculation.numberOfDaysToDeterminateReleaseDate
    val dateHdcAppliesFrom = sentence.sentencedAt
    val hdc180Result = calculateHdced(
      HdcedCalculationInput(
        custodialPeriodDecimal = custodialPeriodDecimal,
        custodialPeriod = custodialPeriod,
        dateHdcAppliesFrom = dateHdcAppliesFrom,
        deductedDays = sentenceCalculation.adjustments.deductions,
        ualToAdd = sentenceCalculation.adjustments.ualDuringCustody,
        otherAdditions = sentenceCalculation.adjustments.awardedDuringCustody -
          sentenceCalculation.adjustments.unusedAdaDays -
          sentenceCalculation.adjustments.servedAdaDays,
        custodialPeriodMidPointDays = hdcedConfiguration.custodialPeriodMidPointDaysPreHdc365,
        custodialPeriodAboveMidpointDeductionDays = hdcedConfiguration.custodialPeriodAboveMidpointDeductionDaysPreHdc365,
        sentence = sentence,
        additionalRulesForBreakdown = setOf(HDC_180),
      ),
    )
    val hdc365Result = calculateHdced(
      HdcedCalculationInput(
        custodialPeriodDecimal = custodialPeriodDecimal,
        custodialPeriod = custodialPeriod,
        dateHdcAppliesFrom = dateHdcAppliesFrom,
        deductedDays = sentenceCalculation.adjustments.deductions,
        ualToAdd = sentenceCalculation.adjustments.ualDuringCustody,
        otherAdditions = sentenceCalculation.adjustments.awardedDuringCustody -
          sentenceCalculation.adjustments.unusedAdaDays -
          sentenceCalculation.adjustments.servedAdaDays,
        custodialPeriodMidPointDays = hdcedConfiguration.custodialPeriodMidPointDaysPostHdc365,
        custodialPeriodAboveMidpointDeductionDays = hdcedConfiguration.custodialPeriodAboveMidpointDeductionDaysPostHdc365,
        sentence = sentence,
        additionalRulesForBreakdown = emptySet(),
      ),
    )

    if (defaultTo365CommencementDateIfConsecSentenceSpansIt(hdc180Result.hdcedWithOnlyUalAdded, sentence).isBefore(HDC_365_COMMENCEMENT_DATE)) {
      setUsingCalculationResult(sentenceCalculation, hdc180Result)
    } else if (hdc365Result.hdcedWithOnlyUalAdded.isAfterOrEqualTo(HDC_365_COMMENCEMENT_DATE)) {
      setUsingCalculationResult(sentenceCalculation, hdc365Result)
    } else {
      val defaultedResult = hdc365Result.copy(adjustedHdced = HDC_365_COMMENCEMENT_DATE.plusDays(hdc365Result.input.otherAdditions))
      setUsingCalculationResult(sentenceCalculation, defaultedResult, HDCED_ADJUSTED_TO_365_COMMENCEMENT)
    }
  }

  private fun calculateHdced(input: HdcedCalculationInput): HdcedCalculationResult = if (input.custodialPeriodDecimal.toLongReleaseDays() < input.custodialPeriodMidPointDays) {
    calculateHdcedUnderMidpoint(input)
  } else {
    calculateHdcedOverMidpoint(input)
  }

  private fun calculateHdcedUnderMidpoint(input: HdcedCalculationInput): HdcedCalculationResult {
    val halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod = max(
      hdcedConfiguration.custodialPeriodBelowMidpointMinimumDeductionDays,
      ReleaseMultiplier.ONE_HALF.applyTo(input.custodialPeriodDecimal),
    )

    val daysWithOnlyUalAdded = halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod.minus(input.deductedDays).plus(input.ualToAdd)
    val dateWithOnlyUalAdded = input.dateHdcAppliesFrom.plusDays(daysWithOnlyUalAdded)
    val dateWithOnlyDeductions = dateWithOnlyUalAdded.minusDays(input.ualToAdd)

    return if (isUnadjustedHdcLessThanTheMinimumHDCPeriod(input.sentence, dateWithOnlyDeductions)) {
      calculateHdcedMinimumCustodialPeriod(input, CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT)
    } else {
      val adjustedDate = dateWithOnlyUalAdded.plusDays(input.otherAdditions)
      val numberOfDaysToAdjustedDate = daysWithOnlyUalAdded.plus(input.otherAdditions)
      HdcedCalculationResult(
        input = input,
        hdcedWithOnlyUalAdded = dateWithOnlyUalAdded,
        adjustedHdced = adjustedDate,
        numberOfDaysToAdjustedHdced = numberOfDaysToAdjustedDate,
        breakdown = ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT) + input.additionalRulesForBreakdown,
          rulesWithExtraAdjustments = mapOf(
            CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT to AdjustmentDuration(
              halfTheCustodialPeriodButAtLeastTheMinimumHDCEDPeriod,
            ),
          ),
          adjustedDays = input.totalAdjustmentForBreakdown(),
          releaseDate = adjustedDate,
          unadjustedDate = input.sentence.sentencedAt,
        ),

      )
    }
  }

  private fun calculateHdcedOverMidpoint(input: HdcedCalculationInput): HdcedCalculationResult {
    val daysWithOnlyUalAdded = input.custodialPeriod
      .minus(input.custodialPeriodAboveMidpointDeductionDays + 1) // Extra plus one because we use the numberOfDaysToDeterminateReleaseDate param and not the sentencedAt param
      .minus(input.deductedDays)
      .plus(input.ualToAdd)
    val dateWithOnlyUalAdded = input.sentence.sentencedAt.plusDays(daysWithOnlyUalAdded)
    val dateWithOnlyDeductions = dateWithOnlyUalAdded.minusDays(input.ualToAdd)

    return if (isUnadjustedHdcLessThanTheMinimumHDCPeriod(input.sentence, dateWithOnlyDeductions)) {
      calculateHdcedMinimumCustodialPeriod(input, CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD)
    } else {
      val adjustedDate = dateWithOnlyUalAdded.plusDays(input.otherAdditions)
      val numberOfDaysToAdjustedDate = daysWithOnlyUalAdded.plus(input.otherAdditions)
      HdcedCalculationResult(
        input = input,
        hdcedWithOnlyUalAdded = dateWithOnlyUalAdded,
        adjustedHdced = adjustedDate,
        numberOfDaysToAdjustedHdced = numberOfDaysToAdjustedDate,
        breakdown = ReleaseDateCalculationBreakdown(
          rules = setOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD) + input.additionalRulesForBreakdown,
          rulesWithExtraAdjustments = mapOf(CalculationRule.HDCED_GE_MIDPOINT_LT_MAX_PERIOD to AdjustmentDuration(-input.custodialPeriodAboveMidpointDeductionDays)),
          adjustedDays = input.totalAdjustmentForBreakdown(),
          releaseDate = adjustedDate,
          unadjustedDate = adjustedDate.plusDays(input.custodialPeriodAboveMidpointDeductionDays),
        ),

      )
    }
  }

  private fun calculateHdcedMinimumCustodialPeriod(
    input: HdcedCalculationInput,
    parentRule: CalculationRule,
  ): HdcedCalculationResult {
    val dateWithOnlyUalAdded = input.sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc).plusDays(input.ualToAdd)
    val adjustedDate = dateWithOnlyUalAdded.plusDays(input.otherAdditions)
    return HdcedCalculationResult(
      input = input,
      hdcedWithOnlyUalAdded = dateWithOnlyUalAdded,
      adjustedHdced = adjustedDate,
      numberOfDaysToAdjustedHdced = hdcedConfiguration.minimumDaysOnHdc.plus(input.ualToAdd).plus(input.otherAdditions),
      breakdown = ReleaseDateCalculationBreakdown(
        rules = setOf(CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD, parentRule) + input.additionalRulesForBreakdown,
        rulesWithExtraAdjustments = mapOf(
          CalculationRule.HDCED_MINIMUM_CUSTODIAL_PERIOD to AdjustmentDuration(
            hdcedConfiguration.minimumDaysOnHdc,
          ),
        ),
        adjustedDays = input.ualToAdd.plus(input.otherAdditions),
        releaseDate = adjustedDate,
        unadjustedDate = input.sentence.sentencedAt,
      ),
    )
  }

  private fun isUnadjustedHdcLessThanTheMinimumHDCPeriod(
    sentence: CalculableSentence,
    hdced: LocalDate,
  ) = sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc).isAfterOrEqualTo(hdced)

  private fun adjustedReleasePointIsLessThanMinimumEligiblePeriod(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
  ) = sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(sentence.sentencedAt.plusDays(hdcedConfiguration.minimumDaysOnHdc))

  private fun unadjustedReleasePointIsLessThanMinimumCustodialPeriod(custodialPeriod: BigDecimal) = custodialPeriod.toLongReleaseDays() < hdcedConfiguration.minimumCustodialPeriodDays

  private fun noHdced(sentenceCalculation: SentenceCalculation) {
    sentenceCalculation.homeDetentionCurfewEligibilityDate = null
    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = 0
    sentenceCalculation.breakdownByReleaseDateType.remove(ReleaseDateType.HDCED)
  }

  /**
   * If a consecutive sentence spans both before and after the HDC365 commencement date,
   * ensure the HDC date defaults to the commencement date when the calculated date falls before it.
   */
  private fun defaultTo365CommencementDateIfConsecSentenceSpansIt(hdcedDate: LocalDate, sentence: CalculableSentence) = if (sentence is ConsecutiveSentence && sentence.hasSentencesBeforeAndAfter(HDC_365_COMMENCEMENT_DATE)) {
    maxOf(hdcedDate, HDC_365_COMMENCEMENT_DATE)
  } else {
    hdcedDate
  }

  private fun setUsingCalculationResult(sentenceCalculation: SentenceCalculation, calculationResult: HdcedCalculationResult, additionalRule: CalculationRule? = null) {
    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = calculationResult.numberOfDaysToAdjustedHdced
    sentenceCalculation.homeDetentionCurfewEligibilityDate = calculationResult.adjustedHdced
    sentenceCalculation.breakdownByReleaseDateType[ReleaseDateType.HDCED] = calculationResult.breakdown.copy(
      rules = calculationResult.breakdown.rules + setOfNotNull(additionalRule),
    )
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
