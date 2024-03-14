package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.BreakdownChangedSinceLastCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.UnsupportedCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BreakdownMissingReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedCalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Service
@Transactional(readOnly = true)
open class DetailedCalculationResultsService(
  private val calculationTransactionalService: CalculationTransactionalService,
  private val prisonApiDataMapper: PrisonApiDataMapper,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationResultEnrichmentService: CalculationResultEnrichmentService,
) {

  @Transactional(readOnly = true)
  fun findDetailedCalculationResults(calculationRequestId: Long): DetailedCalculationResults {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    val calculationUserInputs = transform(calculationRequest.calculationRequestUserInput)
    val prisonerDetails = calculationRequest.prisonerDetails?.let { prisonApiDataMapper.mapPrisonerDetails(calculationRequest) }
    val sentenceAndOffences = calculationRequest.sentenceAndOffences?.let { prisonApiDataMapper.mapSentencesAndOffences(calculationRequest) }
    val bookingAndSentenceAdjustments = calculationRequest.adjustments?.let { prisonApiDataMapper.mapBookingAndSentenceAdjustments(calculationRequest) }
    val returnToCustodyDate = calculationRequest.returnToCustodyDate?.let { prisonApiDataMapper.mapReturnToCustodyDate(calculationRequest) }
    val calculation = transform(calculationRequest)
    var breakdownMissingReason: BreakdownMissingReason? = null
    val calculationBreakdown = if (sentenceAndOffences != null && prisonerDetails != null && bookingAndSentenceAdjustments != null) {
      val booking = Booking(
        offender = transform(prisonerDetails),
        sentences = sentenceAndOffences.map { transform(it, calculationUserInputs) }.flatten(),
        adjustments = transform(bookingAndSentenceAdjustments, sentenceAndOffences),
        bookingId = prisonerDetails.bookingId,
        returnToCustodyDate = returnToCustodyDate?.returnToCustodyDate,
        calculateErsed = calculationUserInputs.calculateErsed,
      )
      try {
        calculationTransactionalService.calculateWithBreakdown(booking, calculation)
      } catch (e: BreakdownChangedSinceLastCalculation) {
        breakdownMissingReason = BreakdownMissingReason.BREAKDOWN_CHANGED_SINCE_LAST_CALCULATION
        null
      } catch(e: UnsupportedCalculationBreakdown) {
        breakdownMissingReason = BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN
        null
      }
    } else {
      breakdownMissingReason = BreakdownMissingReason.PRISON_API_DATA_MISSING
      null
    }
    return DetailedCalculationResults(
      calculationRequestId,
      calculationResultEnrichmentService.addDetailToCalculationDates(calculationRequest, calculationBreakdown),
      prisonerDetails,
      sentenceAndOffences,
      calculationBreakdown,
      breakdownMissingReason,
    )
  }
  private fun getCalculationRequest(calculationRequestId: Long): CalculationRequest {
    return calculationRequestRepository.findById(calculationRequestId).orElseThrow {
      EntityNotFoundException("No calculation results exist for calculationRequestId $calculationRequestId")
    }
  }
}
