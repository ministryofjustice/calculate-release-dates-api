package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculation

@Repository
interface LegacySentenceCalculationRepository : JpaRepository<LegacySentenceCalculation, Long> {

  @Modifying
  @Query("DELETE FROM LegacySentenceCalculation WHERE prisonerId = :prisonerId")
  fun deleteAllByPrisonerId(prisonerId: String)

  @Query("SELECT c FROM LegacySentenceCalculation c WHERE c.prisonerId = :prisonerId")
  fun getByPrisonerId(prisonerId: String): List<LegacySentenceCalculation>
}
