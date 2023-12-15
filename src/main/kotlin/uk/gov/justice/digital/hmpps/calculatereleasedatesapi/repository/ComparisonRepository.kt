package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType

@Repository
interface ComparisonRepository : JpaRepository<Comparison, Long> {

  fun findAllByComparisonTypeIsIn(types: Collection<ComparisonType>): List<Comparison>

  fun findAllByComparisonTypeIsInAndPrisonIsIn(types: Collection<ComparisonType>, includedPrisons: List<String>): List<Comparison>

  fun findByComparisonShortReference(shortReference: String): Comparison?
}
