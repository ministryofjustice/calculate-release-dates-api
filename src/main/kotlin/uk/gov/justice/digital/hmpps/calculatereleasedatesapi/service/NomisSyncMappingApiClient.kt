package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.nomissyncmapping.model.NomisDpsSentenceMapping
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.nomissyncmapping.model.NomisSentenceId

@Service
class NomisSyncMappingApiClient(@Qualifier("nomisSyncMappingApiWebClient") private val webClient: WebClient) {
  private inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
  private val log = LoggerFactory.getLogger(this::class.java)

  fun postNomisToDpsMappingLookup(nomisMappings: List<NomisSentenceId>): List<NomisDpsSentenceMapping> {
    log.info("postNomisToDpsMappingLookup")
    return webClient.post()
      .uri("/api/sentences/nomis")
      .bodyValue(nomisMappings)
      .retrieve()
      .bodyToMono(typeReference<List<NomisDpsSentenceMapping>>())
      .block()!!
  }
}
