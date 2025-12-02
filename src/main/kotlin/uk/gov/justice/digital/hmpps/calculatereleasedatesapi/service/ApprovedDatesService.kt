package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ApprovedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ApprovedDatesInputResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ApprovedDatesInputResponse.Companion.available
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ApprovedDatesInputResponse.Companion.unavailable
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ApprovedDatesUnavailableReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.service.ValidationService
import kotlin.jvm.optionals.getOrElse
import kotlin.jvm.optionals.getOrNull

@Service
class ApprovedDatesService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationSourceDataService: CalculationSourceDataService,
  private val bookingService: BookingService,
  private val validationService: ValidationService,
  private val calculationReasonRepository: CalculationReasonRepository,
  private val calculationTransactionalService: CalculationTransactionalService,
  private val objectMapper: ObjectMapper,
) {
  fun inputsForPrisoner(prisonerId: String): ApprovedDatesInputResponse {
    val latestCalculationRequest = calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId, CalculationStatus.CONFIRMED.name).getOrNull()
    return if (latestCalculationRequest == null) {
      unavailable(ApprovedDatesUnavailableReason.NO_PREVIOUS_CALCULATION)
    } else {
      when (latestCalculationRequest.calculationType) {
        CalculationType.MANUAL_DETERMINATE, CalculationType.MANUAL_INDETERMINATE -> unavailable(ApprovedDatesUnavailableReason.PREVIOUS_CALCULATION_MANUAL)
        CalculationType.GENUINE_OVERRIDE -> unavailable(ApprovedDatesUnavailableReason.PREVIOUS_CALCULATION_GENUINE_OVERRIDE)
        CalculationType.CALCULATED -> handlePreviousIsCalculated(latestCalculationRequest)
      }
    }
  }

  private fun handlePreviousIsCalculated(latestCalculationRequest: CalculationRequest): ApprovedDatesInputResponse {
    val sourceData = calculationSourceDataService.getCalculationSourceData(latestCalculationRequest.prisonerId, SourceDataLookupOptions.default())
    val booking = bookingService.getBooking(sourceData)
    return if (latestCalculationRequest.inputData.hashCode() != objectToJson(booking, objectMapper).hashCode()) {
      unavailable(ApprovedDatesUnavailableReason.INPUTS_CHANGED_SINCE_LAST_CALCULATION)
    } else {
      val calculationUserInputs = CalculationUserInputs(calculateErsed = latestCalculationRequest.calculationRequestUserInput?.calculateErsed ?: false)
      val validationMessages = validationService.validate(sourceData, calculationUserInputs, ValidationOrder.INVALID)
      if (validationMessages.isNotEmpty()) {
        unavailable(ApprovedDatesUnavailableReason.VALIDATION_FAILED)
      } else {
        handleCalculablePrisoner(booking, sourceData, calculationUserInputs, latestCalculationRequest)
      }
    }
  }

  private fun handleCalculablePrisoner(
    booking: Booking,
    sourceData: CalculationSourceData,
    calculationUserInputs: CalculationUserInputs,
    latestCalculationRequest: CalculationRequest,
  ): ApprovedDatesInputResponse {
    val approvedDatesCalculationReason = calculationReasonRepository.findById(APPROVED_DATES_CALC_REASON_ID).getOrElse { throw EntityNotFoundException("Couldn't find the calculation reason for adding approved dates") }
    return try {
      val result = calculationTransactionalService.calculate(
        booking = booking,
        calculationStatus = CalculationStatus.PRELIMINARY,
        sourceData = sourceData,
        reasonForCalculation = approvedDatesCalculationReason,
        calculationUserInputs = calculationUserInputs,
        otherCalculationReason = null,
        calculationFragments = null,
        calculationType = CalculationType.CALCULATED,
      )
      if (haveDatesChanged(latestCalculationRequest, result)) {
        unavailable(ApprovedDatesUnavailableReason.DATES_HAVE_CHANGED)
      } else {
        available(result)
      }
    } catch (e: Exception) {
      log.error("Failed to calculate dates for approved dates inputs for prisoner ${latestCalculationRequest.prisonerId}", e)
      unavailable(ApprovedDatesUnavailableReason.CALCULATION_FAILED)
    }
  }

  private fun haveDatesChanged(
    latestCalculationRequest: CalculationRequest,
    result: CalculatedReleaseDates,
  ): Boolean {
    val latestCalcDates = latestCalculationRequest.calculationOutcomes.mapNotNull { outcome -> outcome.outcomeDate?.let { ApprovedDate(ReleaseDateType.valueOf(outcome.calculationDateType), it) } }.toSet()
    val freshlyCalculatedDates = result.dates.mapNotNull { (type, date) -> date?.let { ApprovedDate(type, date) } }.toSet()
    return latestCalcDates != freshlyCalculatedDates
  }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private const val APPROVED_DATES_CALC_REASON_ID = 6L
  }
}
