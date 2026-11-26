package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacyCalculationReason

interface LegacyCalculationReasonRepository : JpaRepository<LegacyCalculationReason, Long> {
  fun findByCode(code: String): LegacyCalculationReason?
}
