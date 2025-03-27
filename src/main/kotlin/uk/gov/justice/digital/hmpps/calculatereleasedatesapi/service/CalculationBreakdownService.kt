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
  private val sourceDataMapper: SourceDataMapper,
  private val calculationTransactionalService: CalculationTransactionalService,
) {

  fun getBreakdownSafely(calculationRequest: CalculationRequest): Either<BreakdownMissingReason, CalculationBreakdown> {
    val sentenceAndOffences = calculationRequest.sentenceAndOffences?.let { sourceDataMapper.mapSentencesAndOffences(calculationRequest) }
    val prisonerDetails = calculationRequest.prisonerDetails?.let { sourceDataMapper.mapPrisonerDetails(calculationRequest) }
    val calculationUserInputs = transform(calculationRequest.calculationRequestUserInput)
    val bookingAndSentenceAdjustments = calculationRequest.adjustments?.let { sourceDataMapper.mapBookingAndSentenceAdjustments(calculationRequest) }
    val returnToCustodyDate = calculationRequest.returnToCustodyDate?.let { sourceDataMapper.mapReturnToCustodyDate(calculationRequest) }
    val calculation = transform(calculationRequest)
    val historicalTusedData = sourceDataMapper.mapHistoricalTusedData(calculationRequest)
    return if (sentenceAndOffences != null && prisonerDetails != null && bookingAndSentenceAdjustments != null) {
      val booking = Booking(
        offender = transform(prisonerDetails),
        sentences = sentenceAndOffences.map {
          transform(
            sentence = it,
            calculationUserInputs = calculationUserInputs,
            historicalTusedData = historicalTusedData,
          )
        },
        adjustments = transform(bookingAndSentenceAdjustments, sentenceAndOffences),
        bookingId = prisonerDetails.bookingId,
        returnToCustodyDate = returnToCustodyDate?.returnToCustodyDate,
      )
      try {
        calculationTransactionalService.calculateWithBreakdown(booking, calculation, calculationUserInputs).right()
      } catch (e: BreakdownChangedSinceLastCalculation) {
        BreakdownMissingReason.BREAKDOWN_CHANGED_SINCE_LAST_CALCULATION.left()
      } catch (e: UnsupportedCalculationBreakdown) {
        BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left()
      }
    } else {
      BreakdownMissingReason.PRISON_API_DATA_MISSING.left()
    }
  }

  fun getBreakdownUnsafely(
    calculationRequestId: Long,
  ): CalculationBreakdown {
    val calculationUserInputs = calculationTransactionalService.findUserInput(calculationRequestId)
    val prisonerDetails = calculationTransactionalService.findPrisonerDetailsFromCalculation(calculationRequestId)
    val sentenceAndOffences = calculationTransactionalService.findSentenceAndOffencesFromCalculation(calculationRequestId)
    val bookingAndSentenceAdjustments = calculationTransactionalService.findBookingAndSentenceAdjustmentsFromCalculation(calculationRequestId)
    val returnToCustodyDate = calculationTransactionalService.findReturnToCustodyDateFromCalculation(calculationRequestId)
    val calculation = calculationTransactionalService.findCalculationResults(calculationRequestId)
    val booking = Booking(
      offender = transform(prisonerDetails),
      sentences = sentenceAndOffences.map { transform(it, calculationUserInputs) },
      adjustments = transform(bookingAndSentenceAdjustments, sentenceAndOffences),
      bookingId = prisonerDetails.bookingId,
      returnToCustodyDate = returnToCustodyDate?.returnToCustodyDate,
    )
    return calculationTransactionalService.calculateWithBreakdown(booking, calculation, calculationUserInputs)
  }
}
