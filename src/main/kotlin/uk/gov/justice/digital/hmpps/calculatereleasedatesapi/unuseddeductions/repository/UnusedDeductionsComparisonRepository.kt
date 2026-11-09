package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparison

@Repository
interface UnusedDeductionsComparisonRepository : JpaRepository<UnusedDeductionsComparison, Long> {
  fun findByComparisonShortReference(shortReference: String): UnusedDeductionsComparison?

  fun findAllByOrderByCalculatedAtDesc(): List<UnusedDeductionsComparison>
}
