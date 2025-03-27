package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData

@Service
class BookingService() {

  fun getBooking(calculationSourceData: CalculationSourceData, calculationUserInputs: CalculationUserInputs): Booking {
    val prisonerDetails = calculationSourceData.prisonerDetails
    val sentenceAndOffences = calculationSourceData.sentenceAndOffences
    val bookingAndSentenceAdjustments = calculationSourceData.bookingAndSentenceAdjustments
    val movements = calculationSourceData.movements

    val offender = transform(prisonerDetails)
    val adjustments = transform(bookingAndSentenceAdjustments, sentenceAndOffences)
    val sentences = sentenceAndOffences.map { transform(it, calculationUserInputs, calculationSourceData.historicalTusedData) }
    val externalMovements = movements.mapNotNull { transform(it) }

    return Booking(
      offender = offender,
      sentences = sentences,
      adjustments = adjustments,
      bookingId = prisonerDetails.bookingId,
      returnToCustodyDate = calculationSourceData.returnToCustodyDate?.returnToCustodyDate,
      fixedTermRecallDetails = calculationSourceData.fixedTermRecallDetails,
      historicalTusedData = calculationSourceData.historicalTusedData,
      externalMovements = externalMovements,
    )
  }
}
