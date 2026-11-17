package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonRegisterPrison

@Component
class PrisonRegisterApiClient(@param:Qualifier("prisonRegisterApiWebClient") private val webClient: WebClient) {

  private inline fun <reified T : Any> typeReference() = object : ParameterizedTypeReference<T>() {}

  fun getActivePrisons(): List<PrisonRegisterPrison> = webClient.get()
    .uri("/prisons/search?active=true")
    .retrieve()
    .bodyToMono(typeReference<List<PrisonRegisterPrison>>())
    .loggingRetry(log, "getActivePrisons()")
    .block()!!

  fun getPrison(prisonId: String): PrisonRegisterPrison? = webClient.get()
    .uri("/prisons/id/{prisonId}", prisonId)
    .retrieve()
    .bodyToMono(PrisonRegisterPrison::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) { Mono.empty() }
    .loggingRetry(log, "getPrison($prisonId)")
    .block()

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
