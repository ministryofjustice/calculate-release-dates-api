package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service

import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.UserContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BulkComparisonEventPublisher
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ServiceUserService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository.UnusedDeductionsComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository.UnusedDeductionsComparisonRepository

@Service
class UnusedDeductionsComparisonSenderService(
  private val prisonService: PrisonService,
  private val bulkComparisonEventPublisher: BulkComparisonEventPublisher?,
  private val serviceUserService: ServiceUserService,
  private val setupCompleter: UnusedDeductionsComparisonSetupCompleter,
) {

  @Async
  fun processPrison(comparisonId: Long, prison: String, token: String) {
    run(comparisonId, token) {
      val prisoners = prisonService.getCalculablePrisonerByPrison(prison).map { it.prisonerNumber }
      sendMessages(comparisonId, prisoners)
      prisoners.size.toLong()
    }
  }

  @Async
  fun processAllPrisons(comparisonId: Long, token: String) {
    run(comparisonId, token) {
      var count = 0L
      prisonService.getAgenciesByType("INST").map { it.agencyId }.forEach { prison ->
        val prisoners = try {
          prisonService.getCalculablePrisonerByPrison(prison).map { it.prisonerNumber }
        } catch (e: WebClientResponseException.NotFound) {
          log.warn("Skipping prison $prison for unused deductions comparison $comparisonId as prison api does not know it")
          emptyList()
        }
        if (prisoners.isNotEmpty()) {
          sendMessages(comparisonId, prisoners, prison)
        }
        count += prisoners.size
      }
      count
    }
  }

  @Async
  fun processManual(comparisonId: Long, prisonerIds: List<String>, token: String) {
    run(comparisonId, token) {
      sendMessages(comparisonId, prisonerIds)
      prisonerIds.size.toLong()
    }
  }

  private fun run(comparisonId: Long, token: String, sendAndCount: () -> Long) {
    try {
      UserContext.setAuthToken(token)
      setupCompleter.completeSetup(comparisonId, sendAndCount())
    } catch (e: Exception) {
      setupCompleter.handleErrorInSetup(comparisonId, e)
    }
  }

  private fun sendMessages(comparisonId: Long, prisonerIds: List<String>, establishment: String? = null) {
    val publisher = bulkComparisonEventPublisher
      ?: throw IllegalStateException("Bulk comparison publisher is not configured for this environment")
    publisher.sendUnusedDeductionsMessageBatch(comparisonId, prisonerIds, serviceUserService.getUsername(), establishment)
  }

  private companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }
}

/**
 * Handles the transactional parts away from the start of the @Async methods.
 */
@Service
class UnusedDeductionsComparisonSetupCompleter(
  private val comparisonRepository: UnusedDeductionsComparisonRepository,
  private val comparisonPersonRepository: UnusedDeductionsComparisonPersonRepository,
) {

  @Transactional
  fun completeSetup(comparisonId: Long, total: Long) {
    val comparison = comparisonRepository.findById(comparisonId).orElseThrow {
      EntityNotFoundException("The unused deductions comparison $comparisonId could not be found.")
    }
    comparison.numberOfPeopleExpected = total
    comparison.comparisonStatus = ComparisonStatus.PROCESSING
    // Messages may all have been processed before setup finished.
    comparison.numberOfPeopleCompared = comparisonPersonRepository.countByComparisonId(comparisonId)
    if (comparison.numberOfPeopleCompared >= total) {
      comparison.comparisonStatus = ComparisonStatus.COMPLETED
    }
    comparisonRepository.save(comparison)
  }

  @Transactional
  fun handleErrorInSetup(comparisonId: Long, e: Exception) {
    log.error("Error setting up unused deductions comparison $comparisonId", e)
    comparisonRepository.findById(comparisonId).ifPresent {
      it.comparisonStatus = ComparisonStatus.ERROR
      comparisonRepository.save(it)
    }
  }

  private companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }
}
