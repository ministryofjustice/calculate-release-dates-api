package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.OperativeSentenceEnvelopeEntity

@Repository
interface OperativeSentenceEnvelopeRepository : JpaRepository<OperativeSentenceEnvelopeEntity, Long> {
  fun findByCalculationRequestId(calculationRequestId: Long): OperativeSentenceEnvelopeEntity?

  @Query("DELETE FROM OperativeSentenceEnvelopeEntity WHERE calculationRequestId = :calculationRequestId")
  @Modifying
  fun deleteByCalculationRequestId(calculationRequestId: Long)
}
