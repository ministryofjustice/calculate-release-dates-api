package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.ADJUSTED_AFTER_TRANCHE_COMMENCEMENT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_1_COMMENCEMENT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_2_COMMENCEMENT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.SDS_STANDARD_RELEASE_APPLIES
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class TimelineAdjustmentService {
  fun applyTrancheAdjustmentLogic(
    calculation: CalculationResult,
    adjustments: Adjustments,
    trancheAndCommencement: Pair<SDSEarlyReleaseTranche, LocalDate?>,
  ): CalculationResult {
    if (trancheAndCommencement.first !in listOf(
        SDSEarlyReleaseTranche.TRANCHE_1,
        SDSEarlyReleaseTranche.TRANCHE_2,
      )
    ) {
      log.info("No adjustments to apply as tranche is ${calculation.sdsEarlyReleaseTranche}")
      return calculation
    }

    if (calculation.dates[ReleaseDateType.CRD] == null) {
      log.info("No Conditional Release Date (CRD) exists, therefore not applying tranche adjustment logic ")
      return calculation
    }

    return adjustReleaseDatesForTranche(calculation, adjustments, trancheAndCommencement.second!!)
  }

  private fun adjustReleaseDatesForTranche(
    calculation: CalculationResult,
    adjustments: Adjustments,
    commencementDate: LocalDate,
  ): CalculationResult {
    val rulesToHandle = listOf(
      SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_1_COMMENCEMENT,
      SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_2_COMMENCEMENT,
      SDS_STANDARD_RELEASE_APPLIES,
    )

    var updatedCalculation = calculation

    val crd = requireNotNull(calculation.dates[ReleaseDateType.CRD]) {
      "CRD cannot be null. This should have been validated by the calling method."
    }
    val numberOfDaysToAdjust = calculateAdjustmentDaysAfterCommencement(adjustments, commencementDate, crd)

    if (numberOfDaysToAdjust != 0L) {
      for (rule in rulesToHandle) {
        for (releaseDateType in RELEASE_DATE_TYPES_TO_ADJUST) {
          updatedCalculation = adjustReleaseDateForType(
            updatedCalculation,
            numberOfDaysToAdjust,
            commencementDate,
            releaseDateType,
            rule,
          )
        }
      }
    }

    updatedCalculation = determineSuppressSds40Hints(updatedCalculation, commencementDate, adjustments)

    return updatedCalculation
  }

  private fun adjustReleaseDateForType(
    calculation: CalculationResult,
    numberOfDaysToAdjust: Long,
    commencementDate: LocalDate,
    releaseDateType: ReleaseDateType,
    rule: CalculationRule,
  ): CalculationResult {
    val applicableRules = calculation.breakdownByReleaseDateType[releaseDateType]?.rules

    if (applicableRules?.contains(rule) == true) {
      log.info("${releaseDateType.name} before adjustment: ${calculation.dates[releaseDateType]}")

      return applyAdjustment(
        calculation,
        releaseDateType,
        commencementDate,
        rule,
        numberOfDaysToAdjust,
      )
    }

    return calculation
  }

  private fun applyAdjustment(
    calculation: CalculationResult,
    releaseDateType: ReleaseDateType,
    commencementDate: LocalDate,
    rule: CalculationRule,
    numberOfDaysToAdjust: Long,
  ): CalculationResult {
    var updatedCalculation = calculation.copy(
      dates = calculation.dates.plus(
        releaseDateType to calculation.dates[releaseDateType]!!.plusDays(numberOfDaysToAdjust),
      ),
    )
    log.info("$releaseDateType after adjustment: ${updatedCalculation.dates[releaseDateType]}")

    if (updatedCalculation.dates[releaseDateType]?.isBefore(commencementDate) == true &&
      rule in listOf(
        SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_1_COMMENCEMENT,
        SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_2_COMMENCEMENT,
      )
    ) {
      updatedCalculation = updatedCalculation.copy(
        dates = updatedCalculation.dates.plus(releaseDateType to commencementDate),
      )
    }

    return updateBreakdownWithTrancheAdjustedRule(updatedCalculation, releaseDateType, numberOfDaysToAdjust)
  }

  private fun calculateAdjustmentDaysAfterCommencement(
    adjustments: Adjustments,
    commencementDate: LocalDate,
    crd: LocalDate,
  ): Long {
    val ualDays = adjustments.getOrEmptyList(UNLAWFULLY_AT_LARGE)
      .filter { it.appliesToSentencesFrom.isAfterOrEqualTo(commencementDate) && it.appliesToSentencesFrom.isBefore(crd) }
      .sumOf { it.numberOfDays }

    val adaDays = adjustments.getOrEmptyList(ADDITIONAL_DAYS_AWARDED)
      .filter { it.appliesToSentencesFrom.isAfterOrEqualTo(commencementDate) && it.appliesToSentencesFrom.isBefore(crd) }
      .sumOf { it.numberOfDays }

    val radaDays = adjustments.getOrEmptyList(RESTORATION_OF_ADDITIONAL_DAYS_AWARDED)
      .filter { it.appliesToSentencesFrom.isAfterOrEqualTo(commencementDate) && it.appliesToSentencesFrom.isBefore(crd) }
      .sumOf { it.numberOfDays }

    val totalAdjustmentsToApply = ualDays + adaDays - radaDays
    log.info("Total adjustments to apply $totalAdjustmentsToApply for commencement date $commencementDate (UAL $ualDays + ADAs $adaDays + RADA $radaDays)")

    return totalAdjustmentsToApply.toLong()
  }

  private fun determineSuppressSds40Hints(calculation: CalculationResult, trancheCommencementDate: LocalDate, adjustments: Adjustments): CalculationResult {
    val suppressSds40Hints = SUPPRESS_SDS_40_HINT_ADJUSTMENT_TYPES
      .flatMap { adjustments.getOrEmptyList(it) }
      .any { it.appliesToSentencesFrom.isAfterOrEqualTo(trancheCommencementDate) }

    return if (suppressSds40Hints) calculation.copy(showSds40Hints = false) else calculation
  }

  private fun updateBreakdownWithTrancheAdjustedRule(
    calculation: CalculationResult,
    releaseDateType: ReleaseDateType,
    numberOfDaysToAdjust: Long,
  ): CalculationResult {
    val existingBreakdown = calculation.breakdownByReleaseDateType[releaseDateType]
    return calculation.copy(
      breakdownByReleaseDateType = calculation.breakdownByReleaseDateType.plus(
        releaseDateType to existingBreakdown!!.copy(
          rules = existingBreakdown.rules.plus(ADJUSTED_AFTER_TRANCHE_COMMENCEMENT),
          rulesWithExtraAdjustments = existingBreakdown.rulesWithExtraAdjustments.plus(
            ADJUSTED_AFTER_TRANCHE_COMMENCEMENT to AdjustmentDuration(adjustmentValue = numberOfDaysToAdjust),
          ),
        ),
      ),
    )
  }

  companion object {
    val RELEASE_DATE_TYPES_TO_ADJUST = listOf(
      ReleaseDateType.HDCED,
      ReleaseDateType.ERSED,
      ReleaseDateType.PED,
    )

    val SUPPRESS_SDS_40_HINT_ADJUSTMENT_TYPES =
      listOf(REMAND, TAGGED_BAIL, UNLAWFULLY_AT_LARGE, ADDITIONAL_DAYS_AWARDED, RESTORATION_OF_ADDITIONAL_DAYS_AWARDED)
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
