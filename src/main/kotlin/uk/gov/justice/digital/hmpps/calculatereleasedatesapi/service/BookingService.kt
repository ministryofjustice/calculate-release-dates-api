package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence

@Service
class BookingService(
  private val prisonApiClient: PrisonApiClient,
  private val sentenceValidationService: SentenceValidationService
) {
  fun getBooking(prisonerId: String): Booking {
    val prisonerDetails = prisonApiClient.getOffenderDetail(prisonerId)
    val offender = transform(prisonerDetails)
    val sentences = mutableListOf<Sentence>()
    val sentencesAndOffences = prisonApiClient.getSentencesAndOffences(prisonerDetails.bookingId)
    sentenceValidationService.validateSupportedSentences(sentencesAndOffences)
    sentencesAndOffences.forEach { sentences.addAll(transform(it)) }
    val adjustments = transform(prisonApiClient.getSentenceAdjustments(prisonerDetails.bookingId))

    return Booking(
      offender = offender,
      sentences = sentences.toMutableList(),
      adjustments = adjustments,
      bookingId = prisonerDetails.bookingId,
    )
  }
}
