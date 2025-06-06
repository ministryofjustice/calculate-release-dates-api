package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import java.time.LocalDate

@Repository
interface CalculationOutcomeRepository : JpaRepository<CalculationOutcome, Long> {
  @Query(
    nativeQuery = true,
    value = "select * from calculation_outcome where calculation_outcome.calculation_request_id in (select calculation_request_id from comparison_person where comparison_id = ? and mismatch_type='RELEASE_DATES_MISMATCH')",
  )
  fun findForComparisonAndReleaseDatesMismatch(comparisonId: Long): List<CalculationOutcome>

  @Query(
    nativeQuery = true,
    value = """
       SELECT DISTINCT ON (co.calculation_date_type) co.*
        FROM calculation_outcome co
                 JOIN calculation_request cr ON co.calculation_request_id = cr.id
        WHERE cr.prisoner_id = ?1
          AND co.calculation_date_type IN ('SLED', 'LED', 'SED')
          AND co.outcome_date > ?2
          AND cr.calculation_type IN ('CALCULATED', 'MANUAL_OVERRIDE', 'MANUAL_INDETERMINATE', 'MANUAL_DETERMINATE')
          AND cr.calculation_status = 'CONFIRMED'
        ORDER BY co.calculation_date_type, co.outcome_date DESC
        """,
  )
  fun getDominantHistoricDates(prisonerId: String, currentDate: LocalDate): List<CalculationOutcome>
}
