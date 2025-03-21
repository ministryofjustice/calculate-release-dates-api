package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData

@Service
class BookingService() {

  fun getBooking(prisonApiSourceData: PrisonApiSourceData, calculationUserInputs: CalculationUserInputs): Booking {
    val prisonerDetails = prisonApiSourceData.prisonerDetails
    val sentenceAndOffences = prisonApiSourceData.sentenceAndOffences
    val bookingAndSentenceAdjustments = prisonApiSourceData.bookingAndSentenceAdjustments
    val movements = prisonApiSourceData.movements

    val offender = transform(prisonerDetails)
    val adjustments = transform(bookingAndSentenceAdjustments, sentenceAndOffences)
    val sentences = sentenceAndOffences.map { transform(it, calculationUserInputs, prisonApiSourceData.historicalTusedData) }
    val externalMovements = movements.mapNotNull { transform(it) }

    return Booking(
      offender = offender,
      sentences = sentences,
      adjustments = adjustments,
      bookingId = prisonerDetails.bookingId,
      returnToCustodyDate = prisonApiSourceData.returnToCustodyDate?.returnToCustodyDate,
      fixedTermRecallDetails = prisonApiSourceData.fixedTermRecallDetails,
      historicalTusedData = prisonApiSourceData.historicalTusedData,
      externalMovements = externalMovements,
    )
  }
}
