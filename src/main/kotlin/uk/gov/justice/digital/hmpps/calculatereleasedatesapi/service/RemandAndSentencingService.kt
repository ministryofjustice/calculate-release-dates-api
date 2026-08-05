package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing.PrisonerRecallsResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.apiclients.RemandAndSentencingApiClient

@Service
class RemandAndSentencingService(private val remandAndSentencingApi: RemandAndSentencingApiClient) {

  fun getRecallsForOffender(prisonerId: String): PrisonerRecallsResponse = remandAndSentencingApi.getRecallsForOffender(prisonerId, true)
}
