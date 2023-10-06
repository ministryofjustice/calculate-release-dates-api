package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.GenuineOverride
import java.util.Optional
import java.util.UUID

interface GenuineOverrideRepository : JpaRepository<GenuineOverride, Long> {
  fun findAllByOriginalCalculationRequestCalculationReferenceOrderBySavedAtDesc(originalCalculationRequest: UUID): List<GenuineOverride>
  fun findBySavedCalculationCalculationReference(calculationRequestReference: UUID): Optional<GenuineOverride>
}
