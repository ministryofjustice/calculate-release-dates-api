package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome

@Repository
interface CalculationOutcomeRepository : JpaRepository<CalculationOutcome, Long> {
  fun findByCalculationRequestIdIn(calculationRequestIds: List<Long>): List<CalculationOutcome>
}
