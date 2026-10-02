package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequestSentenceOutcome

@Repository
interface CalculationRequestSentenceOutcomeRepository : JpaRepository<CalculationRequestSentenceOutcome, Long> {
  fun findByCalculationRequestSentenceId(calculationRequestSentenceId: Long): List<CalculationRequestSentenceOutcome>

  @Query("DELETE FROM CalculationRequestSentenceOutcome WHERE calculationRequestSentenceId = :calculationRequestSentenceId")
  @Modifying
  fun deleteByCalculationRequestSentenceId(calculationRequestSentenceId: Long)
}
