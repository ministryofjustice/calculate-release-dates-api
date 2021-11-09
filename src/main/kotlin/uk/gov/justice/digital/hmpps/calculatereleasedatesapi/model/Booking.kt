package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType

data class Booking(
  var offender: Offender,
  var sentences: MutableList<Sentence>,
  var adjustments: MutableMap<AdjustmentType, Int> = mutableMapOf(),
  val bookingId: Long = -1L,
) {
  fun getOrZero(adjustmentType: AdjustmentType): Int {
    return if (adjustments.containsKey(adjustmentType) && adjustments[adjustmentType] != null) {
      adjustments[adjustmentType]!!
    } else {
      0
    }
  }

  fun deepCopy(): Booking {
    return this.copy(
      sentences = this.sentences.map(Sentence::deepCopy).toMutableList()
    )
  }
}
