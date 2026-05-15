package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.OperativeSentenceEnvelopeEntity

@Repository
interface OperativeSentenceEnvelopeRepository : JpaRepository<OperativeSentenceEnvelopeEntity, Long> {
  fun findByCalculationRequestId(calculationRequestId: Long): OperativeSentenceEnvelopeEntity?
}
