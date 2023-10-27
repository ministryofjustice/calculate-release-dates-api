package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails

@Service
class AdjustmentsApiClient(@Qualifier("adjustmentsApiWebClient") private val webClient: WebClient) {
    private inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
    private val log = LoggerFactory.getLogger(this::class.java)

    fun getAdjustmentsByPerson(prisonerId: String): PrisonerDetails {
        log.info("Requesting details for prisoner $prisonerId")
        return webClient.get()
            .uri("/adjustments?person=$prisonerId")
            .retrieve()
            .bodyToMono(typeReference<PrisonerDetails>())
            .block()!!
    }

}