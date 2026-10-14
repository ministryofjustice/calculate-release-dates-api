package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequestSentence

@Repository
interface CalculationRequestSentenceRepository : JpaRepository<CalculationRequestSentence, Long> {
  fun findByCalculationRequestId(calculationRequestId: Long): List<CalculationRequestSentence>

  @Query("DELETE FROM CalculationRequestSentence WHERE calculationRequestId = :calculationRequestId")
  @Modifying
  fun deleteByCalculationRequestId(calculationRequestId: Long)
}
