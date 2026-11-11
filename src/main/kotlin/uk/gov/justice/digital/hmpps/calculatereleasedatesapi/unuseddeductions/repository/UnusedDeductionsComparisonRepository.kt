package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparison

@Repository
interface UnusedDeductionsComparisonRepository : JpaRepository<UnusedDeductionsComparison, Long> {
  fun findByComparisonShortReference(shortReference: String): UnusedDeductionsComparison?

  /** Single prison runs are only visible for the given prisons, manual and all prison runs are always visible. */
  @Query(
    """
    select c from UnusedDeductionsComparison c
    where (c.comparisonType <> 'PRISON' or c.prison in :prisons)
    order by c.calculatedAt desc
    """,
  )
  fun findViewable(@Param("prisons") prisons: Collection<String>, pageable: Pageable): Page<UnusedDeductionsComparison>

  @Query(
    """
    select c from UnusedDeductionsComparison c
    where (c.comparisonType <> 'PRISON' or c.prison in :prisons)
    and c.calculatedByUsername = :username
    order by c.calculatedAt desc
    """,
  )
  fun findViewableCalculatedBy(
    @Param("prisons") prisons: Collection<String>,
    @Param("username") username: String,
    pageable: Pageable,
  ): Page<UnusedDeductionsComparison>

  @Query(
    """
    select c from UnusedDeductionsComparison c
    where (c.comparisonType <> 'PRISON' or c.prison in :prisons)
    and c.calculatedByUsername <> :username
    order by c.calculatedAt desc
    """,
  )
  fun findViewableNotCalculatedBy(
    @Param("prisons") prisons: Collection<String>,
    @Param("username") username: String,
    pageable: Pageable,
  ): Page<UnusedDeductionsComparison>
}
