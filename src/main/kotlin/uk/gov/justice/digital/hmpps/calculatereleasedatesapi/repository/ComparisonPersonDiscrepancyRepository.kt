package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancy

@Repository
interface ComparisonPersonDiscrepancyRepository : JpaRepository<ComparisonPersonDiscrepancy, Long> {
  fun existsByComparisonPerson(comparisonPerson: ComparisonPerson): Boolean

  fun findTopByComparisonPersonShortReferenceAndSupersededByIdIsNullOrderByCreatedAtDesc(comparisonPersonShortReference: String): ComparisonPersonDiscrepancy?

  @Query("DELETE FROM ComparisonPersonDiscrepancy WHERE comparisonPerson.calculationRequestId = :calculationRequestId")
  @Modifying
  fun deleteByCalculationRequestId(calculationRequestId: Long)

  @Query("DELETE FROM ComparisonPersonDiscrepancyCause WHERE discrepancy.comparisonPerson.calculationRequestId = :calculationRequestId")
  @Modifying
  fun deleteCauseByCalculationRequestId(calculationRequestId: Long)

  @Query("DELETE FROM ComparisonPersonDiscrepancy WHERE comparisonPerson.id = :comparisonPersonId")
  @Modifying
  fun deleteByComparisonPersonId(comparisonPersonId: Long)

  @Query("DELETE FROM ComparisonPersonDiscrepancyCause WHERE discrepancy.comparisonPerson.id = :comparisonPersonId")
  @Modifying
  fun deleteCauseByComparisonPersonId(comparisonPersonId: Long)
}
