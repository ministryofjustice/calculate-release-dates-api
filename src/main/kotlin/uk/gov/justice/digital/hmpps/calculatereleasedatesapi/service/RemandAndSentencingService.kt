package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing.PrisonerRecallsResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.apiclients.RemandAndSentencingApiClient

@Service
class RemandAndSentencingService(private val remandAndSentencingApi: RemandAndSentencingApiClient) {

  fun getRecallsForOffender(prisonerId: String): Mono<PrisonerRecallsResponse> = remandAndSentencingApi.getRecallsForOffenderAsync(prisonerId, true)
}
