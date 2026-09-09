package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.remandandsentencing.model.Sentence
import java.util.UUID

@Service
class RemandAndSentencingApiClient(@param:Qualifier("remandAndSentencingApiWebClient") private val webClient: WebClient) {
  private val log = LoggerFactory.getLogger(this::class.java)

  fun getSentence(sentenceUuid: UUID): Sentence? {
    log.info("getSentence {}", sentenceUuid)
    return webClient.get()
      .uri("/sentence/{sentenceUuid}", sentenceUuid)
      .retrieve()
      .bodyToMono(Sentence::class.java)
      .block()
  }
}
