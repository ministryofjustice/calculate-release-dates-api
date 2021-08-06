package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class Booking(
  var offender: Offender,
  var sentences: MutableList<Sentence>,
  var adjustments: MutableMap<AdjustmentType, Int> = mutableMapOf()
) {
  fun getOrZero(adjustmentType: AdjustmentType): Int {
    return if (adjustments.containsKey(adjustmentType) && adjustments[adjustmentType] != null) {
      adjustments[adjustmentType]!!
    } else {
      0
    }
  }
}
