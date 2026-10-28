package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DeletedBulkComparisonsResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.businessDaysAgo
import java.time.LocalDate

@Service
class DeleteOldBulkCalculationsService(
  private val comparisonRepository: ComparisonRepository,
  private val comparisonPersonRepository: ComparisonPersonRepository,
  private val deleteCalculationService: DeleteCalculationService,
) {

  @Transactional
  fun deleteBatch(atLeastItems: Int): DeletedBulkComparisonsResult {
    val minAgeForDeletion = LocalDate.now().businessDaysAgo(BUSINESS_DAYS_TO_KEEP)

    var total = 0L
    val comparisonsToDeleteThisTime = comparisonRepository.allComparisonsOlderThanDateExceptTheLatestTwo(minAgeForDeletion).sortedBy { it.calculatedAt }.takeWhile {
      if (total <= atLeastItems) {
        total += it.numberOfComparisons
        true
      } else {
        false
      }
    }

    log.info("Deleting ${comparisonsToDeleteThisTime.size} comparisons containing ${comparisonsToDeleteThisTime.sumOf { it.numberOfComparisons }} comparisons")

    val calcsDeletedSuccessfully = comparisonsToDeleteThisTime.sumOf { comparisonToDelete ->
      log.info("Deleting bulk comparison $comparisonToDelete")
      val comparisonPeople = comparisonPersonRepository.findByComparisonId(comparisonToDelete.id.toLong())
      val numberDeletedSuccessfully = comparisonPeople.count { comparisonPerson ->
        try {
          val calculationRequestId = comparisonPerson.calculationRequestId
          if (calculationRequestId != null) {
            log.info("Deleting bulk comparison calculation request $calculationRequestId")
            deleteCalculationService.deletePreliminaryOrBulk(calculationRequestId)
          } else {
            comparisonPersonRepository.deleteById(comparisonPerson.id!!)
          }
          true
        } catch (e: Exception) {
          log.error("Unable to delete preliminary calculation request: $comparisonToDelete", e)
          false
        }
      }
      comparisonRepository.deleteById(comparisonToDelete.id.toLong())
      numberDeletedSuccessfully
    }
    return DeletedBulkComparisonsResult(comparisonsToDeleteThisTime.sumOf { it.numberOfComparisons }, calcsDeletedSuccessfully.toLong())
  }

  companion object {
    private val log = LoggerFactory.getLogger(DeleteOldBulkCalculationsService::class.java)
    private const val BUSINESS_DAYS_TO_KEEP = 10L
  }
}
