package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.BreakdownChangedSinceLastCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.SourceDataMissingException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.UnsupportedCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BreakdownMissingReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Service
@Transactional(readOnly = true)
@Suppress("RedundantModalityModifier") // required for spring @Transactional
open class CalculationBreakdownService(
  private val sourceDataMapper: SourceDataMapper,
  private val calculationTransactionalService: CalculationTransactionalService,
  private val bookingService: BookingService,
  private val calculationRequestRepository: CalculationRequestRepository,
) {

  fun getBreakdownSafely(calculationRequest: CalculationRequest): Either<BreakdownMissingReason, CalculationBreakdown> {
    val sourceData = try {
      sourceDataMapper.getSourceData(calculationRequest)
    } catch (e: SourceDataMissingException) {
      return BreakdownMissingReason.PRISON_API_DATA_MISSING.left()
    }

    val calculationUserInputs = transform(calculationRequest.calculationRequestUserInput)
    val calculation = transform(calculationRequest)
    val booking = bookingService.getBooking(sourceData)
    return try {
      calculationTransactionalService.calculateWithBreakdown(booking, calculation, calculationUserInputs).right()
    } catch (e: BreakdownChangedSinceLastCalculation) {
      BreakdownMissingReason.BREAKDOWN_CHANGED_SINCE_LAST_CALCULATION.left()
    } catch (e: UnsupportedCalculationBreakdown) {
      BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left()
    }
  }

  fun getBreakdownUnsafely(
    calculationRequestId: Long,
  ): CalculationBreakdown {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    val sourceData = sourceDataMapper.getSourceData(calculationRequest)
    val calculationUserInputs = transform(calculationRequest.calculationRequestUserInput)
    val booking = bookingService.getBooking(sourceData)
    val calculation = transform(calculationRequest)
    return calculationTransactionalService.calculateWithBreakdown(booking, calculation, calculationUserInputs)
  }

  private fun getCalculationRequest(calculationRequestId: Long): CalculationRequest = calculationRequestRepository.findById(calculationRequestId).orElseThrow {
    EntityNotFoundException("No calculation results exist for calculationRequestId $calculationRequestId")
  }
}
