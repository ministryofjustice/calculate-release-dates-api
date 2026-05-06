package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client.loggingRetry
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CouldNotGetMoOffenceInformation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.SdsOffenceDetails

@Service
class ManageOffencesApiClient(@param:Qualifier("manageOffencesApiWebClient") private val webClient: WebClient) {
  private inline fun <reified T : Any> typeReference() = object : ParameterizedTypeReference<T>() {}
  private val log = LoggerFactory.getLogger(this::class.java)

  fun getSdsOffenceDetails(offenceCodes: List<String>): List<SdsOffenceDetails> {
    val offencesList = offenceCodes.joinToString(",")
    log.info("getSdsOffenceDetails : /schedule/sds-offence-details?offenceCodes=$offencesList")

    return webClient.get()
      .uri("/schedule/sds-offence-details?offenceCodes=$offencesList")
      .retrieve()
      .bodyToMono(typeReference<List<SdsOffenceDetails>>())
      .loggingRetry(log, "getSdsOffenceDetails($offencesList)")
      .block() ?: throw CouldNotGetMoOffenceInformation("Sds offence details lookup failed for $offencesList, cannot proceed to perform a sentence calculation")
  }

  fun getToreraOffenceCodes(): List<String> = webClient.get()
    .uri("/schedule/torera-offence-codes")
    .retrieve()
    .bodyToMono(typeReference<List<String>>())
    .loggingRetry(log, "getToreraOffenceCodes()")
    .block() ?: throw CouldNotGetMoOffenceInformation("getToreraOffenceCodes request failed to load")
}
