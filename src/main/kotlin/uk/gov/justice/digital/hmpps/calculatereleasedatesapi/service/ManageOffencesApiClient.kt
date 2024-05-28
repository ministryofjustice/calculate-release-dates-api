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
    val offencesList = if (offenceCodes.size > 1) offenceCodes.joinToString(",") else offenceCodes[0]
    log.info("getPCSCMarkersForOffences : /schedule/pcsc-indicators?offenceCodes=$offencesList")

    return webClient.get()
      .uri("/schedule/pcsc-indicators?offenceCodes=$offencesList")
      .retrieve()
      .bodyToMono(typeReference<List<OffencePcscMarkers>>())
      .retryWhen(
        Retry.backoff(5, Duration.ofMillis(100))
          .maxBackoff(Duration.ofSeconds(3))
          .doBeforeRetry { retrySignal ->
            log.warn("getPCSCMarkersForOffences: Retrying [Attempt: ${retrySignal.totalRetries() + 1}] due to ${retrySignal.failure().message}. ")
          },
      )
      .onErrorMap { _ ->
        CouldNotGetMoOffenceInformation("Sexual or violent schedule for offence lookup failed for ${offenceCodes.joinToString(",")}, cannot proceed to perform a sentence calculation")
      }
      .block()!!
  }

  fun getSexualOrViolentForOffenceCodes(offenceCodes: List<String>): List<SDSEarlyReleaseExclusionForOffenceCode> {
    val offencesList = if (offenceCodes.size > 1) offenceCodes.joinToString(",") else offenceCodes[0]
    log.info("getSexualOrViolentForOffenceCodes : /schedule/sexual-or-violent?offenceCodes=$offencesList")

    return webClient.get()
      .uri("/schedule/sexual-or-violent?offenceCodes=$offencesList")
      .retrieve()
      .bodyToMono(typeReference<List<SDSEarlyReleaseExclusionForOffenceCode>>())
      .retryWhen(
        Retry.backoff(5, Duration.ofMillis(100))
          .maxBackoff(Duration.ofSeconds(3))
          .doBeforeRetry { retrySignal ->
            log.warn("getSexualOrViolentForOffenceCodes: Retrying [Attempt: ${retrySignal.totalRetries() + 1}] due to ${retrySignal.failure().message}. ")
          },
      )
      .onErrorMap { _ ->
        CouldNotGetMoOffenceInformation("Sexual or violent schedule for offence lookup failed for ${offenceCodes.joinToString(",")}, cannot proceed to perform a sentence calculation")
      }
      .block()!!
  }
}
