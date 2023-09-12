package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.GenuineOverride
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CalculationNotFoundException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.GenuineOverrideRepository
import java.util.UUID

@Service
class GenuineOverrideService(
  val genuineOverrideRepository: GenuineOverrideRepository,
  val calculationRequestRepository: CalculationRequestRepository,
) {

  fun createGenuineOverride(genuineOverrideRequest: GenuineOverrideRequest): GenuineOverrideResponse {
    val originalCalculation = calculationRequestRepository.findByCalculationReference(UUID.fromString(genuineOverrideRequest.originalCalculationRequest))
    return originalCalculation.map {
      val genuineOverride = GenuineOverride(
        reason = genuineOverrideRequest.reason,
        originalCalculationRequest = it,
      )
      val savedGenuineOverride = genuineOverrideRepository.save(genuineOverride)
      transform(savedGenuineOverride)
    }.orElseThrow { CalculationNotFoundException("Could not find original calculation with reference ${genuineOverrideRequest.originalCalculationRequest}") }
  }
}
