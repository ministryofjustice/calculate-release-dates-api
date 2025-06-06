package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceIdentificationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.BookingTimelineService

@Service
class CalculationService(
  val sentenceIdentificationService: SentenceIdentificationService,
  private val bookingTimelineService: BookingTimelineService,
) {

  fun calculateReleaseDates(
    booking: Booking,
    calculationUserInputs: CalculationUserInputs,
  ): CalculationOutput {
    val options = CalculationOptions(calculationUserInputs.calculateErsed)
    // identify the types of the sentences
    for (sentence in booking.sentences) {
      sentenceIdentificationService.identify(sentence, booking.offender)
    }

    return bookingTimelineService.calculate(booking.sentences, booking.adjustments, booking.offender, booking.returnToCustodyDate, options, booking.externalMovements)
  }
}
