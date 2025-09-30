package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CouldNotSaveManualEntryException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideCreatedResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.Period.ZERO

@Service
class GenuineOverrideService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val manualCalculationService: ManualCalculationService,
  private val serviceUserService: ServiceUserService,
  private val bookingService: BookingService,
  private val calculationSourceDataService: CalculationSourceDataService,
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
  private val buildProperties: BuildProperties,
  private val objectMapper: ObjectMapper,
) {

  @Transactional
  fun overrideDatesForACalculation(calculationRequestId: Long, genuineOverrideRequest: GenuineOverrideRequest): GenuineOverrideCreatedResponse {
    val originalRequest = getOriginalRequest(calculationRequestId)

    val sourceData = calculationSourceDataService.getCalculationSourceData(originalRequest.prisonerId, InactiveDataOptions.default())
    val booking = bookingService.getBooking(sourceData)

    val newRequest = saveNewRequest(booking, sourceData, originalRequest, genuineOverrideRequest)

    markOldRequestAsOverridden(originalRequest, newRequest, genuineOverrideRequest)

    saveOverriddenDates(genuineOverrideRequest, newRequest)

    writeToNomisAndPublishEvent(booking, genuineOverrideRequest, newRequest)

    return GenuineOverrideCreatedResponse(
      originalCalculationRequestId = originalRequest.id,
      newCalculationRequestId = newRequest.id,
    )
  }

  private fun getOriginalRequest(calculationRequestId: Long): CalculationRequest = calculationRequestRepository.findByIdAndCalculationStatus(
    calculationRequestId,
    PRELIMINARY.name,
  ).orElseThrow {
    EntityNotFoundException("No preliminary calculation exists for calculationRequestId $calculationRequestId")
  }

  private fun saveNewRequest(
    booking: Booking,
    sourceData: CalculationSourceData,
    originalRequest: CalculationRequest,
    genuineOverrideRequest: GenuineOverrideRequest,
  ): CalculationRequest = calculationRequestRepository.save(
    transform(
      booking,
      serviceUserService.getUsername(),
      CalculationStatus.CONFIRMED,
      sourceData,
      originalRequest.reasonForCalculation,
      objectMapper,
      originalRequest.otherReasonForCalculation,
      version = buildProperties.version,
    ).copy(
      calculationType = CalculationType.GENUINE_OVERRIDE,
      overridesCalculationRequestId = originalRequest.id,
      genuineOverrideReason = genuineOverrideRequest.reason,
      genuineOverrideReasonFurtherDetail = genuineOverrideRequest.reasonFurtherDetail,
    ),
  )

  private fun markOldRequestAsOverridden(
    originalRequest: CalculationRequest,
    newRequestWithOriginalInputs: CalculationRequest,
    genuineOverrideRequest: GenuineOverrideRequest,
  ) {
    calculationRequestRepository.save(
      originalRequest.copy(
        calculationStatus = CalculationStatus.OVERRIDDEN.name,
        overriddenByCalculationRequestId = newRequestWithOriginalInputs.id,
        genuineOverrideReason = genuineOverrideRequest.reason,
        genuineOverrideReasonFurtherDetail = genuineOverrideRequest.reasonFurtherDetail,
      ),
    )
  }

  private fun saveOverriddenDates(genuineOverrideRequest: GenuineOverrideRequest, newRequestWithOriginalInputs: CalculationRequest) {
    calculationOutcomeRepository.saveAll(
      genuineOverrideRequest.dates.map {
        CalculationOutcome(
          calculationDateType = it.dateType.name,
          outcomeDate = it.date,
          calculationRequestId = newRequestWithOriginalInputs.id,
        )
      },
    )
  }

  private fun writeToNomisAndPublishEvent(
    booking: Booking,
    genuineOverrideRequest: GenuineOverrideRequest,
    newRequest: CalculationRequest,
  ) {
    val effectiveSentenceLength = try {
      manualCalculationService.calculateEffectiveSentenceLength(booking, genuineOverrideRequest.dates.find { it.dateType == ReleaseDateType.SED }?.date)
    } catch (ex: Exception) {
      log.info("Exception caught calculating ESL for ${newRequest.prisonerId}, setting to zero.", ex)
      ZERO
    }
    manualCalculationService.writeToNomisAndPublishEvent(
      prisonerId = newRequest.prisonerId,
      booking = booking,
      calculationRequestId = newRequest.id,
      calculationOutcomes = emptyList<CalculationOutcome>(),
      isGenuineOverride = true,
      effectiveSentenceLength = effectiveSentenceLength,
    ) ?: throw CouldNotSaveManualEntryException("There was a problem saving the overridden dates to NOMIS")
  }

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
