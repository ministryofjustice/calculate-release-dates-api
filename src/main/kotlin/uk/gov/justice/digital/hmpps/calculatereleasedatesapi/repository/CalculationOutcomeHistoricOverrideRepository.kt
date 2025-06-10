package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcomeHistoricOverride

@Repository
interface CalculationOutcomeHistoricOverrideRepository : JpaRepository<CalculationOutcomeHistoricOverride, Long> {
  fun findByCalculationRequestId(calculationRequestId: Long): List<CalculationOutcomeHistoricOverride>
}
