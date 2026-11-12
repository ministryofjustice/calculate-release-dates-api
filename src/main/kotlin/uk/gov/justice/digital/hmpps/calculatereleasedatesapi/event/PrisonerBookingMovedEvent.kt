package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.event

data class PrisonerBookingMovedEvent(
  val additionalInformation: PrisonerBookingMovedAdditionalInformation,
  val eventType: String,
)

data class PrisonerBookingMovedAdditionalInformation(
  val bookingId: String,
  val movedFromNomsNumber: String,
  val movedToNomsNumber: String,
  val bookingStartDateTime: String,
)
