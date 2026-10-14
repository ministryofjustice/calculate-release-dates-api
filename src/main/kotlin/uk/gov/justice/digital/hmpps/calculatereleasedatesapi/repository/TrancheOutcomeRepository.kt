package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.TrancheOutcome

@Repository
interface TrancheOutcomeRepository : JpaRepository<TrancheOutcome, Long> {

  @Query("DELETE FROM TrancheOutcome WHERE calculationRequest.id = :calculationRequestId")
  @Modifying
  fun deleteByCalculationRequestId(calculationRequestId: Long)
}
