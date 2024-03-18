package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.BreakdownChangedSinceLastCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.UnsupportedCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BreakdownMissingReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown

@Service
@Transactional(readOnly = true)
@Suppress("RedundantModalityModifier") // required for spring @Transactional
open class CalculationBreakdownService(
  private val prisonApiDataMapper: PrisonApiDataMapper,
  private val calculationTransactionalService: CalculationTransactionalService,
) {

  fun getBreakdownSafely(calculationRequest: CalculationRequest): Either<BreakdownMissingReason, CalculationBreakdown> {
    val sentenceAndOffences = calculationRequest.sentenceAndOffences?.let { prisonApiDataMapper.mapSentencesAndOffences(calculationRequest) }
    val prisonerDetails = calculationRequest.prisonerDetails?.let { prisonApiDataMapper.mapPrisonerDetails(calculationRequest) }
    val calculationUserInputs = transform(calculationRequest.calculationRequestUserInput)
    val bookingAndSentenceAdjustments = calculationRequest.adjustments?.let { prisonApiDataMapper.mapBookingAndSentenceAdjustments(calculationRequest) }
    val returnToCustodyDate = calculationRequest.returnToCustodyDate?.let { prisonApiDataMapper.mapReturnToCustodyDate(calculationRequest) }
    val calculation = transform(calculationRequest)
    return if (sentenceAndOffences != null && prisonerDetails != null && bookingAndSentenceAdjustments != null) {
      val booking = Booking(
        offender = transform(prisonerDetails),
        sentences = sentenceAndOffences.map { transform(it, calculationUserInputs) }.flatten(),
        adjustments = transform(bookingAndSentenceAdjustments, sentenceAndOffences),
        bookingId = prisonerDetails.bookingId,
        returnToCustodyDate = returnToCustodyDate?.returnToCustodyDate,
        calculateErsed = calculationUserInputs.calculateErsed,
      )
      try {
        calculationTransactionalService.calculateWithBreakdown(booking, calculation).right()
      } catch (e: BreakdownChangedSinceLastCalculation) {
        BreakdownMissingReason.BREAKDOWN_CHANGED_SINCE_LAST_CALCULATION.left()
      } catch (e: UnsupportedCalculationBreakdown) {
        BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left()
      }
    } else {
      BreakdownMissingReason.PRISON_API_DATA_MISSING.left()
    }
  }

}
