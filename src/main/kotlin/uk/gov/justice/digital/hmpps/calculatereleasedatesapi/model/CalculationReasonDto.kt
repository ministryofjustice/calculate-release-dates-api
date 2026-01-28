package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason

data class CalculationReasonDto(
  val id: Long,
  val isOther: Boolean,
  val displayName: String,
  val useForApprovedDates: Boolean,
  val requiresFurtherDetail: Boolean,
) {
  companion object {
    fun from(entity: CalculationReason) = CalculationReasonDto(
      entity.id!!,
      entity.isOther,
      entity.displayName,
      entity.useForApprovedDates,
      entity.requiresFurtherDetail,
    )
  }
}
