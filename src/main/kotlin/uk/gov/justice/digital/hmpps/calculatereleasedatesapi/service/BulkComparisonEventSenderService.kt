package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.UserContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.MaxRetryAchievedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.prisonapi.model.CalculablePrisoner

@Service
class BulkComparisonEventSenderService(
  private val prisonService: PrisonService,
  private val bulkComparisonEventPublisher: BulkComparisonEventPublisher?,
  private val serviceUserService: ServiceUserService,
  private val bulkComparisonSetupCompleter: BulkComparisonSetupCompleter,
  private val eligiblePrisonsService: EligiblePrisonsService,
) {

  @Async
  fun processPrisonComparison(comparisonId: Long, prison: String, token: String) {
    try {
      setAuthToken(token)
      val prisoners = prisonService.getCalculablePrisonerByPrison(prison)
      sendMessages(comparisonId, prisoners.map { it.prisonerNumber })
      bulkComparisonSetupCompleter.completeSetup(comparisonId, prisoners.size.toLong())
    } catch (e: Exception) {
      bulkComparisonSetupCompleter.handleErrorInBulkSetup(comparisonId, e)
    }
  }

  @Async
  fun processFullCaseLoadComparison(comparisonId: Long, token: String) {
    try {
      setAuthToken(token)
      val prisons = eligiblePrisonsService.eligibleCaseloadPrisons()
      val prisonToPrisonersMap = mutableMapOf<String, List<CalculablePrisoner>>()
      val failedPrisons = mutableListOf<String>()
      for (prison in prisons.eligible) {
        try {
          prisonToPrisonersMap[prison] = prisonService.getCalculablePrisonerByPrison(prison)
        } catch (e: Exception) {
          if (!isPrisonLevelFailure(e)) throw e
          log.warn("Comparison $comparisonId skipping prison $prison, prison-api could not provide calculable prisoners", e)
          failedPrisons.add(prison)
        }
      }

      if (prisons.eligible.isNotEmpty() && failedPrisons.size == prisons.eligible.size) {
        throw IllegalStateException("Comparison $comparisonId failed for every eligible prison: $failedPrisons")
      }

      var count = 0L
      prisonToPrisonersMap.forEach { (prison, prisoners) ->
        sendMessages(comparisonId, prisoners.map { it.prisonerNumber }, prison)
        count += prisoners.size
      }

      log.info(
        "Bulk comparison setup comparison=$comparisonId caseload=${prisons.caseload.size} eligible=${prisons.eligible.size} " +
          "inactiveSkipped=${prisons.inactive} successful=${prisonToPrisonersMap.size} failed=$failedPrisons " +
          "prisoners=$count registerAvailable=${prisons.registerAvailable}",
      )
      bulkComparisonSetupCompleter.completeSetup(comparisonId, count, failedPrisons)
    } catch (e: Exception) {
      bulkComparisonSetupCompleter.handleErrorInBulkSetup(comparisonId, e)
    }
  }

  @Async
  fun processManualComparison(comparisonId: Long, prisonerIds: List<String>, token: String) {
    try {
      setAuthToken(token)
      sendMessages(comparisonId, prisonerIds)
      bulkComparisonSetupCompleter.completeSetup(comparisonId, prisonerIds.size.toLong())
    } catch (e: Exception) {
      bulkComparisonSetupCompleter.handleErrorInBulkSetup(comparisonId, e)
    }
  }

  fun sendMessages(comparisonId: Long, calculations: List<String>, establishment: String? = null) {
    if (bulkComparisonEventPublisher == null) {
      throw IllegalStateException("Bulk comparison publisher is not configured for this environment")
    }
    bulkComparisonEventPublisher.sendMessageBatch(
      comparisonId = comparisonId,
      persons = calculations,
      establishment = establishment,
      username = serviceUserService.getUsername(),
    )
  }

  fun setAuthToken(token: String) {
    UserContext.setAuthToken(token)
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    internal fun isPrisonLevelFailure(e: Exception): Boolean = when (e) {
      is WebClientResponseException -> e.statusCode.value() in setOf(400, 404)
      is MaxRetryAchievedException -> true
      else -> false
    }
  }
}
