package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.dbhelper

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * You can add methods here for test specific manipulations that require a transaction without making the test itself transactional.
 */
@Service
class TestTransactionalService(
  private val calculationRequestRepository: TestCalculationRequestRepository,
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
  private val comparisonRepository: ComparisonRepository,
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

  @Transactional
  fun setComparisonCalculatedAt(comparison: ComparisonDto, newComparisonTimestamp: LocalDateTime) {
    val comparison = comparisonRepository.findByComparisonShortReference(comparison.comparisonShortReference)!!
    comparisonRepository.saveAndFlush(comparison.copy(calculatedAt = newComparisonTimestamp))
  }
}
