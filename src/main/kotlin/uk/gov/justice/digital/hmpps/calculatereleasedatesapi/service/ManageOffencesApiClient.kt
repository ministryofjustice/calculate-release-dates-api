package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.retry.Retry
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CouldNotGetMoOffenceInformation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SDSEarlyReleaseExclusionForOffenceCode
import java.time.Duration

@Service
class ManageOffencesApiClient(@Qualifier("manageOffencesApiWebClient") private val webClient: WebClient) {
  private inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
  private val log = LoggerFactory.getLogger(this::class.java)

  fun getPCSCMarkersForOffences(offenceCodes: List<String>): List<OffencePcscMarkers> {
    val offencesList = offenceCodes.joinToString(",")
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
            throw MaxRetryAchievedException("getSexualOrViolentForOffenceCodes: Max retries - lookup failed for $offencesList, cannot proceed to perform a sentence calculation")
          },
      )
      .block() ?: throw CouldNotGetMoOffenceInformation("PCSC indicator for offence lookup, otherwise failed for $offencesList, cannot proceed to perform a sentence calculation")
  }

  fun getSexualOrViolentForOffenceCodes(offenceCodes: List<String>): List<SDSEarlyReleaseExclusionForOffenceCode> {
    val offencesList = offenceCodes.joinToString(",")
    log.info("getSexualOrViolentForOffenceCodes : /schedule/sexual-or-violent?offenceCodes=$offencesList")

    return webClient.get()
      .uri("/schedule/sexual-or-violent?offenceCodes=$offencesList")
      .retrieve()
      .bodyToMono(typeReference<List<SDSEarlyReleaseExclusionForOffenceCode>>())
      .retryWhen(
        Retry
          .backoff(5, Duration.ofMillis(100))
          .maxBackoff(Duration.ofSeconds(5))
          .doBeforeRetry { retrySignal ->
            log.warn("getSexualOrViolentForOffenceCodes: Retrying [Attempt: ${retrySignal.totalRetries() + 1}] due to ${retrySignal.failure().message}. ")
          }
          .onRetryExhaustedThrow { _, _ ->
            throw MaxRetryAchievedException("getSexualOrViolentForOffenceCodes: Max retries - lookup failed for $offencesList, cannot proceed to perform a sentence calculation")
          },
      )
      .block() ?: throw CouldNotGetMoOffenceInformation("Sexual or violent schedule otherwise failed for offence lookup failed for $offencesList, cannot proceed to perform a sentence calculation")
  }

  class MaxRetryAchievedException(message: String?) : RuntimeException(message)
}
