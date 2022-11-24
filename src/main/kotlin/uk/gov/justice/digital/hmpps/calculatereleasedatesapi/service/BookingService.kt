package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.ValidationException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService

@Service
class BookingService(
  private val validationService: ValidationService,
  private val calculationTransactionalService: CalculationTransactionalService,
) {

  fun getBooking(prisonApiSourceData: PrisonApiSourceData, calculationUserInputs: CalculationUserInputs?): Booking {
    val prisonerDetails = prisonApiSourceData.prisonerDetails
    val sentenceAndOffences = prisonApiSourceData.sentenceAndOffences
    val bookingAndSentenceAdjustments = prisonApiSourceData.bookingAndSentenceAdjustments
    val validationMessages = validationService.validate(prisonApiSourceData)
    if (validationMessages.isNotEmpty()) {
      var message = "The validation has failed with errors:"
      validationMessages.forEach { message += "\n    " + it.message }
      throw ValidationException(message)
    }
    val offender = transform(prisonerDetails)
    val adjustments = transform(bookingAndSentenceAdjustments, sentenceAndOffences)
    val sentences = sentenceAndOffences.map { transform(it, calculationUserInputs) }.flatten()

    return Booking(
      offender = offender,
      sentences = sentences,
      adjustments = adjustments,
      bookingId = prisonerDetails.bookingId,
      returnToCustodyDate = prisonApiSourceData.returnToCustodyDate?.returnToCustodyDate
    )
  }

  fun getCalculationBreakdown(
    calculationRequestId: Long
  ): CalculationBreakdown {
    val calculationUserInputs = calculationTransactionalService.findUserInput(calculationRequestId)
    val prisonerDetails = calculationTransactionalService.findPrisonerDetailsFromCalculation(calculationRequestId)
    val sentenceAndOffences = calculationTransactionalService.findSentenceAndOffencesFromCalculation(calculationRequestId)
    val bookingAndSentenceAdjustments = calculationTransactionalService.findBookingAndSentenceAdjustmentsFromCalculation(calculationRequestId)
    val returnToCustodyDate = calculationTransactionalService.findReturnToCustodyDateFromCalculation(calculationRequestId)
    val calculation = calculationTransactionalService.findCalculationResults(calculationRequestId)
    val booking = Booking(
      offender = transform(prisonerDetails),
      sentences = sentenceAndOffences.map { transform(it, calculationUserInputs) }.flatten(),
      adjustments = transform(bookingAndSentenceAdjustments, sentenceAndOffences),
      bookingId = prisonerDetails.bookingId,
      returnToCustodyDate = returnToCustodyDate?.returnToCustodyDate
    )
    return calculationTransactionalService.calculateWithBreakdown(booking, calculation)
  }
}
