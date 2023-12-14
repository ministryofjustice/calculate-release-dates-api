package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType

@Repository
interface ComparisonRepository : JpaRepository<Comparison, Long> {

  fun findAllByTypeIsIn(types: Collection<ComparisonType>): List<Comparison>

  fun findAllByTypeIsInAndPrisonIsIn(types: Collection<ComparisonType>, includedPrisons: List<String>): List<Comparison>

  fun findByManualInputAndComparisonShortReference(manualInput: Boolean, shortReference: String): Comparison?
}
