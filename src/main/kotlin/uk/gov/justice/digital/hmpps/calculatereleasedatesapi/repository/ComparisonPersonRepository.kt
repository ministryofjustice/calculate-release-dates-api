package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson

interface ComparisonPersonRepository : JpaRepository<ComparisonPerson, Long> {

  fun countByComparisonId(comparisonId: Long): Long

  fun findByComparisonIdIsAndIsMatchFalse(comparisonId: Long): List<ComparisonPerson>

  fun findByComparisonIdAndShortReference(comparisonId: Long, shortReference: String): ComparisonPerson?

  fun findByComparisonIdIsAndHdcedFourPlusDateIsNotNull(id: Long): List<ComparisonPerson>
}
