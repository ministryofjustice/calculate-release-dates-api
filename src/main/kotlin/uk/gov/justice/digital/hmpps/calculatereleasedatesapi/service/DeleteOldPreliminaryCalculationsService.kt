package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DeletedPreviousCalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDateTime

@Service
class DeleteOldPreliminaryCalculationsService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val deleteCalculationService: DeleteCalculationService,
) {

  @Transactional
  fun deleteBatch(minAgeInYears: Int, maxItems: Int): DeletedPreviousCalculationResult {
    val minAge = LocalDateTime.now().minusYears(minAgeInYears.toLong())
    val calcsToDelete = calculationRequestRepository.findPreliminaryCalculationRequestIdsOlderThan(minAge, maxItems)
    val calcsDeletedSuccessfully = calcsToDelete.count { calculationRequestId ->
      try {
        deleteCalculationService.delete(calculationRequestId)
        true
      } catch (e: Exception) {
        log.error("Unable to delete preliminary calculation request: $calculationRequestId", e)
        false
      }
    }
    return DeletedPreviousCalculationResult(calcsToDelete.size, calcsDeletedSuccessfully)
  }

  companion object {
    private val log = LoggerFactory.getLogger(DeleteOldPreliminaryCalculationsService::class.java)
  }
}
