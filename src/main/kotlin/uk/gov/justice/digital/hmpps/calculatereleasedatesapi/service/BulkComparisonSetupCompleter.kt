package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BulkComparisonEventHandlerService.Companion.log

/**
 * Class to handle transctional functions away from start of an @Async method.
 */
@Service
class BulkComparisonSetupCompleter(
  private val comparisonRepository: ComparisonRepository,
) {

  @Transactional
  fun completeSetup(comparisonId: Long, total: Long) {
    val comparison = getComparison(comparisonId)
    comparison.numberOfPeopleExpected = total
    comparison.comparisonStatus = ComparisonStatus.PROCESSING
    comparisonRepository.save(comparison)
  }

  @Transactional
  fun handleErrorInBulkSetup(comparisonId: Long, e: Exception) {
    log.error("Error setting up bulk comparison $comparisonId", e)
    val comparison = getComparison(comparisonId)
    comparison.comparisonStatus = ComparisonStatus.ERROR
    comparisonRepository.save(comparison)
  }

  fun getComparison(comparisonId: Long): Comparison = comparisonRepository.findById(comparisonId).orElseThrow {
    EntityNotFoundException("The comparison $comparisonId could not be found.")
  }
}
