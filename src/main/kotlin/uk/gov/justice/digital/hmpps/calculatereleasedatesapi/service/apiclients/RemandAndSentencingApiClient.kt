package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.apiclients

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client.loggingRetry
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing.Recall

@Service
class RemandAndSentencingApiClient(
  @param:Qualifier("remandAndSentencingApiWebClient") private val userAuthWebClient: WebClient,
) {
  private inline fun <reified T : Any> typeReference() = object : ParameterizedTypeReference<T>() {}

  private val log = LoggerFactory.getLogger(this::class.java)

  fun getRecallsForOffender(prisonerId: String, includeAllPeriods: Boolean, bookingId: String? = null): List<Recall> {
    log.info("Requesting getRecallsForOffender for $prisonerId")
    return userAuthWebClient.get()
      .uri {
        it.path("/recall/person/$prisonerId/search")
          .queryParam("includeAllPeriods", includeAllPeriods)
          .apply { if (bookingId != null) queryParam("bookingId", bookingId) }
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<List<Recall>>())
      .loggingRetry(log, "getRecallsForOffender($prisonerId)")
      .block()!!
  }
}
