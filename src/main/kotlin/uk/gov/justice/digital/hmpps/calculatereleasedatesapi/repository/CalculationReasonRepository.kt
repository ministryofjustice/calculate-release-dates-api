package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason

interface CalculationReasonRepository : JpaRepository<CalculationReason, Long> {
  fun findAllByIsActiveTrueOrderById(): List<CalculationReason>

  fun findTopByIsBulkTrue(): Optional<CalculationReason>
}
