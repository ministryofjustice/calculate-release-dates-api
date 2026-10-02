package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.dbhelper

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class TestCalculationRequestService(private val calculationRequestRepository: TestCalculationRequestRepository) {

  @Transactional
  fun updateCalculatedAt(calculationRequestId: Long, calculatedAt: LocalDateTime) {
    calculationRequestRepository.updateCalculatedAt(calculationRequestId, calculatedAt)
  }
}
