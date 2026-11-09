package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonPerson

@Repository
interface UnusedDeductionsComparisonRepository : JpaRepository<UnusedDeductionsComparison, Long> {
  fun findByComparisonShortReference(shortReference: String): UnusedDeductionsComparison?

  fun findAllByOrderByCalculatedAtDesc(): List<UnusedDeductionsComparison>
}

@Repository
interface UnusedDeductionsComparisonPersonRepository : JpaRepository<UnusedDeductionsComparisonPerson, Long> {
  fun findByComparisonId(comparisonId: Long): List<UnusedDeductionsComparisonPerson>

  fun findByComparisonIdAndPerson(comparisonId: Long, person: String): List<UnusedDeductionsComparisonPerson>

  fun findByComparisonIdAndShortReference(comparisonId: Long, shortReference: String): UnusedDeductionsComparisonPerson?

  fun countByComparisonId(comparisonId: Long): Long

  fun countByComparisonIdAndOutcomeIn(comparisonId: Long, outcomes: Collection<UnusedDeductionsComparisonOutcome>): Long
}
