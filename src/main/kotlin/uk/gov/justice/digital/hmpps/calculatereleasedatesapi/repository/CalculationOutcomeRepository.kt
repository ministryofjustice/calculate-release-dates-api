package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome

@Repository
interface CalculationOutcomeRepository : JpaRepository<CalculationOutcome, Long> {
  @Query(
    nativeQuery = true,
    value = "select * from calculation_outcome where calculation_outcome.calculation_request_id in (select calculation_request_id from comparison_person where comparison_id = ? and mismatch_type='RELEASE_DATES_MISMATCH')",
  )
  fun findForComparisonAndReleaseDatesMismatch(comparisonId: Long): List<CalculationOutcome>
}
