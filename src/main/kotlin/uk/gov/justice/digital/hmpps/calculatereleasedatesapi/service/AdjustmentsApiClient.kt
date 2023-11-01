package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.AdjustmentEffectiveDays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.AdjustmentServiceAdjustment
import java.util.UUID

@Service
class AdjustmentsApiClient(@Qualifier("adjustmentsApiWebClient") private val webClient: WebClient) {
  private inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
  private val log = LoggerFactory.getLogger(this::class.java)

  fun getAdjustmentsByPerson(prisonerId: String): List<AdjustmentServiceAdjustment> {
    log.info("Getting adjustment details for prisoner $prisonerId")
    return webClient.get()
      .uri("/adjustments?person=$prisonerId")
      .retrieve()
      .bodyToMono(typeReference<List<AdjustmentServiceAdjustment>>())
      .block()!!
  }

  fun updateEffectiveDays(effectiveDays: AdjustmentEffectiveDays) {
    log.info("Updating effective days details for prisoner ${effectiveDays.person}")
    webClient.post()
      .uri("/adjustments/effective-days")
      .bodyValue(effectiveDays)
      .retrieve()
      .toBodilessEntity()
      .block()!!
  }

  fun updateAdjustment(adjustment: AdjustmentServiceAdjustment) {
    log.info("Updating adjustment details for prisoner ${adjustment.person}")
    webClient.put()
      .uri("/adjustments/${adjustment.id}")
      .bodyValue(adjustment)
      .retrieve()
      .toBodilessEntity()
      .block()!!
  }

  fun createAdjustment(adjustment: AdjustmentServiceAdjustment) {
    log.info("Create adjustment details for prisoner ${adjustment.person}")
    webClient.post()
      .uri("/adjustments")
      .bodyValue(adjustment)
      .retrieve()
      .toBodilessEntity()
      .block()!!
  }

  fun deleteAdjustment(id: UUID) {
    log.info("Delete adjustment details")
    webClient.delete()
      .uri("/adjustments/$id")
      .retrieve()
      .toBodilessEntity()
      .block()!!
  }
}
