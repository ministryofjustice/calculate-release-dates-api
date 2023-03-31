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

  fun getOrZero(vararg adjustmentTypes: AdjustmentType, adjustmentsAfter: LocalDate): Int {
    return adjustmentTypes.mapNotNull { adjustmentType ->
      if (adjustments.containsKey(adjustmentType)) {
        val adjustments = adjustments[adjustmentType]!!
        val adjustmentDays = adjustments.filter { it.appliesToSentencesFrom.isAfter(adjustmentsAfter) }
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
}
