package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence

@Service
class BookingService(
  private val prisonApiClient: PrisonApiClient,
) {
  fun getBooking(prisonerId: String): Booking {
    val prisonerDetails = prisonApiClient.getOffenderDetail(prisonerId)
    val offender = transform(prisonerDetails)
    val sentences = mutableListOf<Sentence>()
    val sentenceAndOffences = prisonApiClient.getSentencesAndOffences(prisonerDetails.bookingId)
    val adjustments = transform(prisonApiClient.getSentenceAndBookingAdjustments(prisonerDetails.bookingId), sentenceAndOffences)
    sentenceAndOffences.forEach { sentences.addAll(transform(it)) }

    return Booking(
      offender = offender,
      sentences = sentences.toMutableList(),
      adjustments = adjustments,
      bookingId = prisonerDetails.bookingId,
    )
  }
}
