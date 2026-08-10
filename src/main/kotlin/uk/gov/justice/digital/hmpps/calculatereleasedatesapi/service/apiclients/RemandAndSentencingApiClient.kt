package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.apiclients

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client.loggingRetry
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing.PrisonerRecallsResponse

@Service
class RemandAndSentencingApiClient(
  @param:Qualifier("remandAndSentencingApiWebClient") private val userAuthWebClient: WebClient,
) {
  private inline fun <reified T : Any> typeReference() = object : ParameterizedTypeReference<T>() {}

  private val log = LoggerFactory.getLogger(this::class.java)

  fun getRecallsForOffenderAsync(prisonerId: String, includeAllPeriods: Boolean, bookingId: String? = null): Mono<PrisonerRecallsResponse> {
    log.info("Requesting getRecallsForOffender for $prisonerId")
    return userAuthWebClient.get()
      .uri {
        it.path("/recall/person/$prisonerId/search")
          .queryParam("includeAllPeriods", includeAllPeriods)
          .apply { if (bookingId != null) queryParam("bookingId", bookingId) }
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<PrisonerRecallsResponse>())
      .loggingRetry(log, "getRecallsForOffender($prisonerId)")
  }
}
