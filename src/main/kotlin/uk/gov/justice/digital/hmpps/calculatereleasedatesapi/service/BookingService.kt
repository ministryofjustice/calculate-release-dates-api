package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence

@Service
class BookingService(
  private val prisonService: PrisonApiClient,
) {
  fun getBooking(prisonerId: String): Booking {
    val prisonerDetails = prisonService.getOffenderDetail(prisonerId)
    val offender = transform(prisonerDetails)
    val sentences = mutableListOf<Sentence>()
    prisonService.getSentencesAndOffences(prisonerDetails.bookingId).forEach { sentences.addAll(transform(it)) }
    val adjustments = transform(prisonService.getSentenceAdjustments(prisonerDetails.bookingId))

    return Booking(
      offender = offender,
      sentences = sentences.toMutableList(),
      adjustments = adjustments,
    )
  }
}
