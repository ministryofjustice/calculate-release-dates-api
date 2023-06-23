package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison

@Repository
interface ComparisonRepository : JpaRepository<Comparison, Long> {

  fun findAllByManualInput(boolean: Boolean): List<Comparison>

  fun findAllByManualInputAndPrisonIsIn(boolean: Boolean, includedPrisons: List<String>): List<Comparison>

  fun findByComparisonShortReference(shortReference: String): Comparison?
}
