package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CouldNotGetMoOffenceInformation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SDSEarlyReleaseExclusionForOffenceCode

@Service
class ManageOffencesApiClient(@Qualifier("manageOffencesApiWebClient") private val webClient: WebClient) {
  private inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
  private val log = LoggerFactory.getLogger(this::class.java)

  fun getPCSCMarkersForOffences(offenceCodes: List<String>): List<OffencePcscMarkers> {
    val offencesList = if (offenceCodes.size > 1) offenceCodes.joinToString(",") else offenceCodes[0]
    log.info("/schedule/pcsc-indicators?offenceCodes=$offencesList")

    return webClient.get()
      .uri("/schedule/pcsc-indicators?offenceCodes=$offencesList")
      .retrieve()
      .bodyToMono(typeReference<List<OffencePcscMarkers>>())
      .onErrorMap {
        CouldNotGetMoOffenceInformation(
          "PCSC indicator for offence lookup failed for $offencesList," +
            " can not proceed to perform a sentence calculation",
        )
      }
      .block()!!
  }
  fun getSexualOrViolentForOffenceCodes(offenceCodes: List<String>): List<SDSEarlyReleaseExclusionForOffenceCode> {
    val offencesList = if (offenceCodes.size > 1) offenceCodes.joinToString(",") else offenceCodes[0]
    log.info("/schedule/sexual-or-violent?offenceCodes=$offencesList")

    return webClient.get()
      .uri("/schedule/sexual-or-violent?offenceCodes=$offencesList")
      .retrieve()
      .bodyToMono(typeReference<List<SDSEarlyReleaseExclusionForOffenceCode>>())
      .onErrorMap {
        CouldNotGetMoOffenceInformation(
          "Sexual or violent schedule for offence lookup failed for $offencesList," +
            " can not proceed to perform a sentence calculation",
        )
      }
      .block()!!
  }
}
