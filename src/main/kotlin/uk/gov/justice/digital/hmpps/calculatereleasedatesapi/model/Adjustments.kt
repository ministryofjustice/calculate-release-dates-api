package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
import java.time.LocalDate

data class Adjustments
// @JsonAnyGetter will add properties from this map to this object, ie. {REMAND: [...]} rather than {adjustments:{REMAND: [...]}}
(@JsonAnyGetter private val adjustments: MutableMap<AdjustmentType, MutableList<Adjustment>> = mutableMapOf()) {

  fun getOrZero(vararg adjustmentTypes: AdjustmentType, adjustmentsBefore: LocalDate, adjustmentsAfter: LocalDate?): Int {
    return adjustmentTypes.mapNotNull { adjustmentType ->
      if (adjustments.containsKey(adjustmentType)) {
        val adjustments = adjustments[adjustmentType]!!
        val adjustmentDays = adjustments.filter { it.appliesToSentencesFrom.isBeforeOrEqualTo(adjustmentsBefore) && (adjustmentsAfter == null || it.appliesToSentencesFrom.isAfter(adjustmentsAfter)) }
          .map { it.numberOfDays }
        adjustmentDays.reduceOrNull { acc, it -> acc + it }
      } else {
        0
      }
    }.reduceOrNull { acc, it -> acc + it } ?: 0
  }

  fun getOrEmptyList(adjustmentType: AdjustmentType): List<Adjustment> {
    return adjustments.getOrDefault(adjustmentType, listOf())
  }

  fun addAdjustment(adjustmentType: AdjustmentType, adjustment: Adjustment) {
    if (!adjustments.containsKey(adjustmentType)) {
      adjustments[adjustmentType] = mutableListOf()
    }
    adjustments[adjustmentType]!!.add(adjustment)
  }

  fun applyPeriodsOfUALIncrementally(
    startDate: LocalDate?,
    initialEndDate: LocalDate,
  ): Int {
    val ualPeriods = mutableSetOf<Adjustment>()

    gatherUALPeriods(this.adjustments, ualPeriods, startDate, initialEndDate)

    return ualPeriods.sumOf { it.numberOfDays }
  }

  private fun gatherUALPeriods(
    adjustmentMap: Map<AdjustmentType, List<Adjustment>>,
    ualPeriods: MutableSet<Adjustment>,
    currentStartDate: LocalDate?,
    currentEndDate: LocalDate,
  ) {
    val ual = AdjustmentType.UNLAWFULLY_AT_LARGE

    val ualAdjustments = adjustmentMap[ual]?.filter {
      it.appliesToSentencesFrom.isAfter(currentStartDate ?: LocalDate.MIN) &&
        it.appliesToSentencesFrom.isBeforeOrEqualTo(currentEndDate)
    } ?: listOf()

    ualAdjustments.forEach { adjustment ->
      // Add the adjustment to the set to avoid duplicates
      if (ualPeriods.add(adjustment)) {
        // Recursively gather further UAL periods with the new end date
        gatherUALPeriods(adjustmentMap, ualPeriods, adjustment.appliesToSentencesFrom, currentEndDate)
      }
    }
  }
}
