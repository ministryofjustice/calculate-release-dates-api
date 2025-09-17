package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.UserContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.prisonapi.model.CalculablePrisoner

@Service
class BulkComparisonEventSenderService(
  private val prisonService: PrisonService,
  private val bulkComparisonEventPublisher: BulkComparisonEventPublisher?,
  private val serviceUserService: ServiceUserService,
  private val bulkComparisonSetupCompleter: BulkComparisonSetupCompleter,
) {

  @Async
  fun processPrisonComparison(comparisonId: Long, prison: String, token: String) {
    try {
      setAuthToken(token)
      val prisoners = prisonService.getCalculablePrisonerByPrison(prison!!)
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
      val currentUserPrisonsList = prisonService.getCurrentUserPrisonsList()
      val prisonToPrisonersMap = mutableMapOf<String, List<CalculablePrisoner>>()
      for (prison in currentUserPrisonsList) {
        prisonToPrisonersMap[prison] = prisonService.getCalculablePrisonerByPrison(prison)
      }

      var count = 0L
      prisonToPrisonersMap.forEach { prison, prisoners ->
        sendMessages(comparisonId, prisoners.map { it.prisonerNumber }, prison)
        count += prisoners.size
      }
      bulkComparisonSetupCompleter.completeSetup(comparisonId, count)
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
}
