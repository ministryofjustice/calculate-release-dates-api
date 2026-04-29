package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client.loggingRetry
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CouldNotGetMoOffenceInformation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.OffenceSdsExclusion

@Service
class ManageOffencesApiClient(@param:Qualifier("manageOffencesApiWebClient") private val webClient: WebClient) {
  private inline fun <reified T : Any> typeReference() = object : ParameterizedTypeReference<T>() {}
  private val log = LoggerFactory.getLogger(this::class.java)

  fun getPCSCMarkersForOffences(offenceCodes: List<String>): List<OffencePcscMarkers> {
    val offencesList = offenceCodes.toSortedSet().joinToString(",")
    log.info("getPCSCMarkersForOffences : /schedule/pcsc-indicators?offenceCodes=$offencesList")

    return webClient.get()
      .uri("/schedule/pcsc-indicators?offenceCodes=$offencesList")
      .retrieve()
      .bodyToMono(typeReference<List<OffencePcscMarkers>>())
      .loggingRetry(log, "getPCSCMarkersForOffences($offencesList)")
      .block() ?: throw CouldNotGetMoOffenceInformation("PCSC indicator for offence lookup, otherwise failed for $offencesList, cannot proceed to perform a sentence calculation")
  }

  fun getSdsExclusionsForOffenceCodes(offenceCodes: List<String>): List<OffenceSdsExclusion> {
    val offencesList = offenceCodes.joinToString(",")
    log.info("getSdsExclusionsForOffenceCodes : /schedule/sds-early-release-exclusions?offenceCodes=$offencesList")

    return webClient.get()
      .uri("/schedule/sds-early-release-exclusions?offenceCodes=$offencesList")
      .retrieve()
      .bodyToMono(typeReference<List<OffenceSdsExclusion>>())
      .loggingRetry(log, "getSdsExclusionsForOffenceCodes($offencesList)")
      .block() ?: throw CouldNotGetMoOffenceInformation("Sds early release exclusions schedule otherwise failed for offence lookup failed for $offencesList, cannot proceed to perform a sentence calculation")
  }

  fun getToreraOffenceCodes(): List<String> = webClient.get()
    .uri("/schedule/torera-offence-codes")
    .retrieve()
    .bodyToMono(typeReference<List<String>>())
    .loggingRetry(log, "getToreraOffenceCodes()")
    .block() ?: throw CouldNotGetMoOffenceInformation("getToreraOffenceCodes request failed to load")
}
