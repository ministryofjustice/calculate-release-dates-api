package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType

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

  @Query(
    """
    SELECT DISTINCT c.prison FROM Comparison c
    WHERE c.comparisonType IN :types
    AND c.calculatedByUsername = :calculatedByUsername
    """,
  )
  fun findDistinctPrisonByComparisonTypeInAndCalculatedByUsername(
    @Param("types") types: Collection<ComparisonType>,
    @Param("calculatedByUsername") calculatedByUsername: String,
  ): List<String>

  @Query(
    """
    SELECT DISTINCT c.prison FROM Comparison c
    WHERE c.comparisonType IN :types
    AND c.calculatedByUsername <> :excludedUsername
    """,
  )
  fun findDistinctPrisonByComparisonTypeInAndCalculatedByUsernameNot(
    @Param("types") types: Collection<ComparisonType>,
    @Param("excludedUsername") excludedUsername: String,
  ): List<String>
}
