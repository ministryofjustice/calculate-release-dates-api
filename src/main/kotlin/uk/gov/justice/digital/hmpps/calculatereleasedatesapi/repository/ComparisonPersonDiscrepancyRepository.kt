package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPersonDiscrepancy

@Repository
interface ComparisonPersonDiscrepancyRepository : JpaRepository<ComparisonPersonDiscrepancy, Long> {
  fun existsByComparisonPerson(comparisonPerson: ComparisonPerson): Boolean

  fun findTopByComparisonPersonShortReferenceAndSupersededByIdIsNullOrderByCreatedAtDesc(comparisonPersonShortReference: String): ComparisonPersonDiscrepancy?
}
