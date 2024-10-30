package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson

interface ComparisonPersonRepository : JpaRepository<ComparisonPerson, Long> {

  fun countByComparisonId(comparisonId: Long): Long

  fun findByComparisonIdIsAndIsMatchFalse(comparisonId: Long): List<ComparisonPerson>

  fun findByComparisonIdAndShortReference(comparisonId: Long, shortReference: String): ComparisonPerson?

  @Query(
    """
      SELECT cr
            FROM CalculationRequest cr
            JOIN cr.reasonForCalculation reason
            JOIN ComparisonPerson cp ON cp.calculationRequestId = cr.id
            JOIN Comparison comparison ON cp.comparisonId = comparison.id
      WHERE comparison.comparisonShortReference = :comparisonShortReference
       AND cp.shortReference = :shortReference
  """,
  )
  fun getJsonForBulkComparison(comparisonShortReference: String, shortReference: String): CalculationRequest?
}
