
package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto

@Service
class AdjustmentsApiClient(@Qualifier("adjustmentsApiWebClient") private val webClient: WebClient) {
  private inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
  private val log = LoggerFactory.getLogger(this::class.java)

  fun getAdjustmentsByPerson(prisonerId: String, statuses: List<AdjustmentDto.Status>, currentPeriodOfCustody: Boolean = true): List<AdjustmentDto> {
    log.info("Getting adjustment details for prisoner $prisonerId")
    return webClient.get()
      .uri { builder ->
        builder.path("/adjustments")
        builder.queryParam("currentPeriodOfCustody", currentPeriodOfCustody)
        builder.queryParam("person", prisonerId)
        builder.queryParam("status", statuses.map { it.toString() })
        builder.build()
      }
      .retrieve()
      .bodyToMono(typeReference<List<AdjustmentDto>>())
      .block()!!
      .filter {
        val isNomisAdjustmentWithZeroOrLessDays = it.source == AdjustmentDto.Source.NOMIS && it.effectiveDays!! <= 0
        !isNomisAdjustmentWithZeroOrLessDays
      }
  }
}
