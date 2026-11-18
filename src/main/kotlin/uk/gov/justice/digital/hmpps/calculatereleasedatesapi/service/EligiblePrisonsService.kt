package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client.PrisonRegisterApiClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.MaxRetryAchievedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonRegisterPrison

@Service
class EligiblePrisonsService(
  private val prisonService: PrisonService,
  private val prisonRegisterApiClient: PrisonRegisterApiClient,
) {

  fun eligibleCaseloadPrisons(): EligiblePrisons {
    val caseload = prisonService.getCurrentUserPrisonsList()
    return when (val lookup = activePrisons()) {
      is ActivePrisonsLookup.Found -> {
        val (eligible, inactive) = caseload.partition { it in lookup.prisonIds }
        EligiblePrisons(caseload = caseload, eligible = eligible, inactive = inactive, registerAvailable = true)
      }
      is ActivePrisonsLookup.Unavailable -> {
        log.warn("prison-register unavailable, falling back to the unfiltered caseload", lookup.cause)
        EligiblePrisons(caseload = caseload, eligible = caseload, inactive = emptyList(), registerAvailable = false)
      }
    }
  }

  fun checkPrisonCanBeCompared(prisonId: String) {
    val prison = try {
      prisonRegisterApiClient.getPrison(prisonId)
    } catch (e: Exception) {
      if (isRegisterUnavailable(e)) {
        log.warn("prison-register unavailable, not validating prison $prisonId before bulk comparison", e)
        return
      }
      throw e
    }
    when {
      prison == null -> throw CrdWebException("Bulk calculation cannot be run for unknown prison $prisonId.", HttpStatus.BAD_REQUEST, "UNKNOWN_PRISON")
      !prison.active -> throw CrdWebException("Bulk calculation cannot be run for inactive prison $prisonId.", HttpStatus.BAD_REQUEST, "INACTIVE_PRISON")
    }
  }

  fun activePrisons(): ActivePrisonsLookup = try {
    ActivePrisonsLookup.Found(
      prisonRegisterApiClient.getActivePrisons()
        .filter(PrisonRegisterPrison::active)
        .map(PrisonRegisterPrison::prisonId)
        .toSet(),
    )
  } catch (e: Exception) {
    if (isRegisterUnavailable(e)) ActivePrisonsLookup.Unavailable(e) else throw e
  }

  private fun isRegisterUnavailable(e: Exception): Boolean = when (e) {
    is MaxRetryAchievedException -> true
    is WebClientRequestException -> true
    is WebClientResponseException -> e.statusCode.value() !in AUTH_FAILURES
    else -> false
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val AUTH_FAILURES = setOf(401, 403)
  }
}

sealed interface ActivePrisonsLookup {
  data class Found(val prisonIds: Set<String>) : ActivePrisonsLookup
  data class Unavailable(val cause: Exception) : ActivePrisonsLookup
}

data class EligiblePrisons(
  val caseload: List<String>,
  val eligible: List<String>,
  val inactive: List<String>,
  val registerAvailable: Boolean,
)
