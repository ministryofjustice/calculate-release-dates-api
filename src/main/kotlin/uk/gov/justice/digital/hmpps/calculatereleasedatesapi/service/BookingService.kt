package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.ValidationException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType

@Service
class BookingService(
  private val prisonService: PrisonService,
  private val validationService: ValidationService,
) {
  fun getBooking(prisonerId: String): Booking {
    val prisonerDetails = prisonService.getOffenderDetail(prisonerId)
    val sentenceAndOffences = prisonService.getSentencesAndOffences(prisonerDetails.bookingId)
    val sentenceAndBookingAdjustments = prisonService.getSentenceAndBookingAdjustments(prisonerDetails.bookingId)
    val validation = validationService.validate(sentenceAndOffences, sentenceAndBookingAdjustments)
    if (validation.type != ValidationType.VALID) {
      throw ValidationException(validation.toErrorString())
    }
    val offender = transform(prisonerDetails)
    val sentences = mutableListOf<Sentence>()
    val adjustments = transform(sentenceAndBookingAdjustments, sentenceAndOffences)
    sentenceAndOffences.forEach { sentences.addAll(transform(it)) }

    return Booking(
      offender = offender,
      sentences = sentences.toMutableList(),
      adjustments = adjustments,
      bookingId = prisonerDetails.bookingId,
    )
  }
}
