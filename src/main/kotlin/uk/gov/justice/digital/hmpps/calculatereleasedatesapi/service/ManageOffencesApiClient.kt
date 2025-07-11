package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.retry.Retry
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CouldNotGetMoOffenceInformation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.SDSEarlyReleaseExclusionForOffenceCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.ToreraSchedulePartCodes
import java.time.Duration

@Service
class ManageOffencesApiClient(@Qualifier("manageOffencesApiWebClient") private val webClient: WebClient) {
  private inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
  private val log = LoggerFactory.getLogger(this::class.java)

  fun getPCSCMarkersForOffences(offenceCodes: List<String>): List<OffencePcscMarkers> {
    val offencesList = offenceCodes.toSortedSet().joinToString(",")
    log.info("getPCSCMarkersForOffences : /schedule/pcsc-indicators?offenceCodes=$offencesList")

    return webClient.get()
      .uri("/schedule/pcsc-indicators?offenceCodes=$offencesList")
      .retrieve()
      .bodyToMono(typeReference<List<OffencePcscMarkers>>())
      .retryWhen(
        Retry.backoff(5, Duration.ofMillis(100))
          .maxBackoff(Duration.ofSeconds(5))
          .doBeforeRetry { retrySignal ->
            log.warn("getPCSCMarkersForOffences: Retrying [Attempt: ${retrySignal.totalRetries() + 1}] due to ${retrySignal.failure().message}. ")
          }
          .onRetryExhaustedThrow { _, _ ->
            throw MaxRetryAchievedException("getPCSCMarkersForOffences: Max retries - lookup failed for $offencesList, cannot proceed to perform a sentence calculation")
          },
      )
      .block() ?: throw CouldNotGetMoOffenceInformation("PCSC indicator for offence lookup, otherwise failed for $offencesList, cannot proceed to perform a sentence calculation")
  }

  fun getSdsExclusionsForOffenceCodes(offenceCodes: List<String>): List<SDSEarlyReleaseExclusionForOffenceCode> {
    val offencesList = offenceCodes.joinToString(",")
    log.info("getSdsExclusionsForOffenceCodes : /schedule/sds-early-release-exclusions?offenceCodes=$offencesList")

    return webClient.get()
      .uri("/schedule/sds-early-release-exclusions?offenceCodes=$offencesList")
      .retrieve()
      .bodyToMono(typeReference<List<SDSEarlyReleaseExclusionForOffenceCode>>())
      .retryWhen(
        Retry
          .backoff(5, Duration.ofMillis(100))
          .maxBackoff(Duration.ofSeconds(5))
          .doBeforeRetry { retrySignal ->
            log.warn("getSdsExclusionsForOffenceCodes: Retrying [Attempt: ${retrySignal.totalRetries() + 1}] due to ${retrySignal.failure().message}. ")
          }
          .onRetryExhaustedThrow { _, _ ->
            throw MaxRetryAchievedException("getSdsExclusionsForOffenceCodes: Max retries - lookup failed for $offencesList, cannot proceed to perform a sentence calculation")
          },
      )
      .block() ?: throw CouldNotGetMoOffenceInformation("Sds early release exclusions schedule otherwise failed for offence lookup failed for $offencesList, cannot proceed to perform a sentence calculation")
  }

  fun getToreraOffenceCodes(): List<String> = webClient.get()
    .uri("/schedule/torera-offence-codes")
    .retrieve()
    .bodyToMono(typeReference<List<String>>())
    .retryWhen(
      Retry
        .backoff(5, Duration.ofMillis(100))
        .maxBackoff(Duration.ofSeconds(5))
        .doBeforeRetry { retrySignal ->
          log.warn("getToreraOffenceCodes: Retrying [Attempt: ${retrySignal.totalRetries() + 1}] due to ${retrySignal.failure().message}. ")
        }
        .onRetryExhaustedThrow { _, _ ->
          throw MaxRetryAchievedException("getToreraOffenceCodes: Max retries - lookup failed, cannot proceed to perform a sentence calculation")
        },
    )
    .block() ?: throw CouldNotGetMoOffenceInformation("getToreraOffenceCodes request failed to load")

  fun getToreraCodesByParts(): ToreraSchedulePartCodes = webClient.get()
    .uri("/schedule/torera-offence-codes-by-schedule-part")
    .retrieve()
    .bodyToMono(typeReference<ToreraSchedulePartCodes>())
    .retryWhen(
      Retry
        .backoff(5, Duration.ofMillis(100))
        .maxBackoff(Duration.ofSeconds(5))
        .doBeforeRetry { retrySignal ->
          log.warn("getToreraCodesByParts: Retrying [Attempt: ${retrySignal.totalRetries() + 1}] due to ${retrySignal.failure().message}. ")
        }
        .onRetryExhaustedThrow { _, _ ->
          throw MaxRetryAchievedException("getToreraCodesByParts: Max retries - lookup failed, cannot proceed to perform a sentence calculation")
        },
    )
    .block() ?: throw CouldNotGetMoOffenceInformation("getToreraCodesByParts request failed to load")

  class MaxRetryAchievedException(message: String?) : RuntimeException(message)
}
