package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments

data class AdjustmentsSourceData(val prisonApiData: BookingAndSentenceAdjustments? = null, val adjustmentsApiData: List<AdjustmentDto>? = null) {
  init {
    if (prisonApiData == null && adjustmentsApiData == null) {
      error("One source of adjustments data must be present")
    }
    if (prisonApiData != null && adjustmentsApiData != null) {
      error("Only one source of adjustments data must be present")
    }
  }

  fun <A> fold(prisonApiConsumer: (prisonApiData: BookingAndSentenceAdjustments) -> A, adjustmentsApiConsumer: (adjustmentsApiData: List<AdjustmentDto>) -> A): A = if (prisonApiData != null) {
    prisonApiConsumer(prisonApiData)
  } else {
    adjustmentsApiConsumer(adjustmentsApiData!!)
  }

  fun appendOlderBooking(data: AdjustmentsSourceData): AdjustmentsSourceData = fold(
    { this.appendOlderBookingPrisonApi(data.prisonApiData!!) },
    { this.appendOlderBookingAdjustmentsApi(data.adjustmentsApiData!!) },
  )

  private fun appendOlderBookingPrisonApi(data: BookingAndSentenceAdjustments): AdjustmentsSourceData = AdjustmentsSourceData(
    prisonApiData = BookingAndSentenceAdjustments(
      bookingAdjustments = this.prisonApiData!!.bookingAdjustments + data.bookingAdjustments,
      sentenceAdjustments = this.prisonApiData.sentenceAdjustments + data.sentenceAdjustments,
    ),
  )

  private fun appendOlderBookingAdjustmentsApi(data: List<AdjustmentDto>): AdjustmentsSourceData = AdjustmentsSourceData(
    adjustmentsApiData = this.adjustmentsApiData!! + data,
  )
}
