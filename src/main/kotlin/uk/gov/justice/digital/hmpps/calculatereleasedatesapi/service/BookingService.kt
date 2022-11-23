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
    validate(prisonApiSourceData)
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
    val sourceData = getSourceData(calculationRequestId)
    val booking = getBookingForBreakdown(
      sourceData,
      calculationRequestId,
      calculationTransactionalService.findUserInput(calculationRequestId)
    )
    val calculation = calculationTransactionalService.findCalculationResults(calculationRequestId)
    return calculationTransactionalService.calculateWithBreakdown(booking, calculation)
  }

  private fun getBookingForBreakdown(
    sourceData: PrisonApiSourceData,
    calculationRequestId: Long,
    calculationUserInputs: CalculationUserInputs?,
  ): Booking = Booking(
      offender = transform(sourceData.prisonerDetails),
      sentences = sourceData.sentenceAndOffences.map { transform(it, calculationUserInputs) }.flatten(),
      adjustments = transform(sourceData.bookingAndSentenceAdjustments, sourceData.sentenceAndOffences),
      bookingId = sourceData.prisonerDetails.bookingId,
      returnToCustodyDate = calculationTransactionalService.findReturnToCustodyDateFromCalculation(calculationRequestId)?.returnToCustodyDate
    )

  private fun getSourceData(calculationRequestId: Long): PrisonApiSourceData =
    PrisonApiSourceData(
      sentenceAndOffences = calculationTransactionalService.findSentenceAndOffencesFromCalculation(calculationRequestId),
      prisonerDetails = calculationTransactionalService.findPrisonerDetailsFromCalculation(calculationRequestId),
      bookingAndSentenceAdjustments = calculationTransactionalService.findBookingAndSentenceAdjustmentsFromCalculation(
        calculationRequestId
      ),
      returnToCustodyDate = calculationTransactionalService.findReturnToCustodyDateFromCalculation(calculationRequestId),
    )

  private fun validate(prisonApiSourceData: PrisonApiSourceData) {
    val validationMessages = validationService.validate(prisonApiSourceData)
    if (validationMessages.isNotEmpty()) {
      var message = "The validation has failed with errors:"
      validationMessages.forEach { message += "\n    " + it.message }
      throw ValidationException(message)
    }
  }
}
