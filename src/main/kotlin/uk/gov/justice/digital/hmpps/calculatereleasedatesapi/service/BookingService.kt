package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.ValidationException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType

@Service
class BookingService(
  private val validationService: ValidationService
) {

  fun getBooking(prisonApiSourceData: PrisonApiSourceData): Booking {
    val prisonerDetails = prisonApiSourceData.prisonerDetails
    val sentenceAndOffences = prisonApiSourceData.sentenceAndOffences
    val bookingAndSentenceAdjustments = prisonApiSourceData.bookingAndSentenceAdjustments
    val validation = validationService.validate(prisonApiSourceData)
    if (validation.type != ValidationType.VALID) {
      throw ValidationException(validation.toErrorString())
    }
    val offender = transform(prisonerDetails)
    val adjustments = transform(bookingAndSentenceAdjustments, sentenceAndOffences)
    val sentences = sentenceAndOffences.map { transform(it) }.flatten()

    return Booking(
      offender = offender,
      sentences = sentences,
      adjustments = adjustments,
      bookingId = prisonerDetails.bookingId,
      returnToCustodyDate = prisonApiSourceData.returnToCustodyDate?.returnToCustodyDate
    )
  }
}
