package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.NativeQuery
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import java.time.LocalDate

@Repository
interface ComparisonRepository : JpaRepository<Comparison, Long> {

  fun findAllByComparisonTypeIsIn(types: Collection<ComparisonType>): List<Comparison>

  fun findAllByComparisonTypeIsInAndPrisonIsIn(types: Collection<ComparisonType>, includedPrisons: List<String>): List<Comparison>

  fun findAllByComparisonTypeIsInAndPrisonIsInAndCalculatedByUsername(
    types: Collection<ComparisonType>,
    includedPrisons: List<String>,
    calculatedByUsername: String,
    pageable: Pageable,
  ): Page<Comparison>

  fun findAllByComparisonTypeIsInAndPrisonIsInAndCalculatedByUsernameNot(
    types: Collection<ComparisonType>,
    includedPrisons: List<String>,
    excludedUsername: String,
    pageable: Pageable,
  ): Page<Comparison>

  fun findByComparisonShortReference(shortReference: String): Comparison?

  @NativeQuery(
    """
      WITH ranked_comparisons AS (
          SELECT
              c.*,
              ROW_NUMBER() OVER (ORDER BY c.calculated_at DESC) AS rn
          FROM comparison c
      )
      SELECT id,
             (SELECT count(*) FROM comparison_person cp WHERE cp.comparison_id = rc.id) as number_of_comparisons,
             calculated_at
      FROM ranked_comparisons rc
      WHERE rc.rn > 2
        AND rc.calculated_at < :minAgeForDeletion
          ORDER BY rc.calculated_at DESC;
    """,
  )
  fun allComparisonsOlderThanDateExceptTheLatestTwo(minAgeForDeletion: LocalDate): List<ComparisonToDelete>
}
