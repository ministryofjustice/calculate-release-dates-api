package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.dbhelper

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDateTime

@Repository
interface TestCalculationRequestRepository : CalculationRequestRepository {

  @Query("UPDATE CalculationRequest SET calculatedAt = :calculatedAt WHERE id = :calculationRequestId")
  @Modifying
  fun updateCalculatedAt(calculationRequestId: Long, calculatedAt: LocalDateTime)
}
