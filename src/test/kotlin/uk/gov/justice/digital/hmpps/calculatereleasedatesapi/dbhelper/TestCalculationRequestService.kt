package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.dbhelper

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.service.DateValidationService
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class TestCalculationRequestService(
  private val calculationRequestRepository: TestCalculationRequestRepository,
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
  private val dateValidationService: DateValidationService,
) {

  @Transactional
  fun updateCalculatedAt(calculationRequestId: Long, calculatedAt: LocalDateTime) {
    calculationRequestRepository.updateCalculatedAt(calculationRequestId, calculatedAt)
  }

  @Transactional
  fun addATused(calculationRequestId: Long, tused: LocalDate) {
    calculationOutcomeRepository.save(
      CalculationOutcome(
        calculationRequestId = calculationRequestId,
        calculationDateType = "TUSED",
        outcomeDate = tused,
      ),
    )
  }
}
