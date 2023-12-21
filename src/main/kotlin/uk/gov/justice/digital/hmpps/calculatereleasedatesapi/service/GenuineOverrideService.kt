package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.GenuineOverride
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CalculationNotFoundException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CouldNotSaveGenuineOverrideException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.GenuineOverrideNotFoundException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideDateRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideDateResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.GenuineOverrideRepository
import java.util.UUID

@Service
class GenuineOverrideService(
  val genuineOverrideRepository: GenuineOverrideRepository,
  val calculationRequestRepository: CalculationRequestRepository,
  val manualCalculationService: ManualCalculationService,
) {

  fun createGenuineOverride(genuineOverrideRequest: GenuineOverrideRequest): GenuineOverrideResponse {
    val originalCalculation = calculationRequestRepository.findByCalculationReference(UUID.fromString(genuineOverrideRequest.originalCalculationRequest))
    val savedCalc = try {
      calculationRequestRepository.findByCalculationReference(UUID.fromString(genuineOverrideRequest.savedCalculation)).get()
    } catch (ex: NullPointerException) {
      null
    }
    return originalCalculation.map {
      val genuineOverride = GenuineOverride(
        reason = genuineOverrideRequest.reason,
        originalCalculationRequest = it,
        isOverridden = genuineOverrideRequest.isOverridden,
        savedCalculation = savedCalc,
      )
      val savedGenuineOverride = genuineOverrideRepository.save(genuineOverride)
      transform(savedGenuineOverride)
    }.orElseThrow { CalculationNotFoundException("Could not find original calculation with reference ${genuineOverrideRequest.originalCalculationRequest}") }
  }

  @Transactional
  fun storeGenuineOverrideDates(genuineOverrideRequest: GenuineOverrideDateRequest): GenuineOverrideDateResponse {
    val originalCalculation = calculationRequestRepository.findByCalculationReference(UUID.fromString(genuineOverrideRequest.originalCalculationReference))
    return originalCalculation.map { it ->
      val storeManualCalculation = manualCalculationService.storeManualCalculation(it.prisonerId, genuineOverrideRequest.manualEntryRequest, MANUALLY_ENTERED_OVERRIDE)
      val overridesForCalculation = genuineOverrideRepository.findAllByOriginalCalculationRequestCalculationReferenceOrderBySavedAtDesc(UUID.fromString(genuineOverrideRequest.originalCalculationReference))
      if (overridesForCalculation.isNotEmpty()) {
        return@map calculationRequestRepository.findById(storeManualCalculation.calculationRequestId).map {
          val mostRecentOverrideRequest = overridesForCalculation[0]
          mostRecentOverrideRequest.savedCalculation = it
          GenuineOverrideDateResponse(it.calculationReference.toString(), genuineOverrideRequest.originalCalculationReference)
        }.orElseThrow { CouldNotSaveGenuineOverrideException("Could not find new calculation to store against Genuine Override") }
      } else {
        throw CouldNotSaveGenuineOverrideException("No overrides existed for the original calculation reference ${genuineOverrideRequest.originalCalculationReference}")
      }
    }.orElseThrow { CouldNotSaveGenuineOverrideException("Could not find new calculation to store against Genuine Override") }
  }

  fun getGenuineOverride(calculationReference: String): GenuineOverrideResponse {
    return genuineOverrideRepository.findBySavedCalculationCalculationReference(UUID.fromString(calculationReference)).map {
      GenuineOverrideResponse(it.reason, it.originalCalculationRequest.calculationReference.toString(), it.savedCalculation?.calculationReference.toString(), it.isOverridden)
    }.orElseThrow { GenuineOverrideNotFoundException("Could not find genuine override for reference: $calculationReference") }
  }

  companion object {
    private const val MANUALLY_ENTERED_OVERRIDE = "The information shown was manually recorded in the Calculate release dates service by Specialist Support. The calculation ID is: %s"
  }
}
