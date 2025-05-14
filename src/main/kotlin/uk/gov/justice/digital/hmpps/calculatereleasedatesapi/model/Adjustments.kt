package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType

data class Adjustments(
// @JsonAnyGetter will add properties from this map to this object, ie. {REMAND: [...]} rather than {adjustments:{REMAND: [...]}}(
  @JsonAnyGetter private val adjustments: MutableMap<AdjustmentType, MutableList<Adjustment>> = mutableMapOf(),
) {

  @JsonAnySetter
  fun add(type: String, adjustmentsList: List<Adjustment>) {
    adjustments[AdjustmentType.valueOf(type)] = adjustmentsList.toMutableList()
  }

  fun entries(): MutableSet<MutableMap.MutableEntry<AdjustmentType, MutableList<Adjustment>>> = adjustments.entries

  fun getOrEmptyList(adjustmentType: AdjustmentType): List<Adjustment> = adjustments.getOrDefault(adjustmentType, listOf())

  fun addAdjustment(adjustmentType: AdjustmentType, adjustment: Adjustment) {
    if (!adjustments.containsKey(adjustmentType)) {
      adjustments[adjustmentType] = mutableListOf()
    }
    adjustments[adjustmentType]!!.add(adjustment)
  }
}
