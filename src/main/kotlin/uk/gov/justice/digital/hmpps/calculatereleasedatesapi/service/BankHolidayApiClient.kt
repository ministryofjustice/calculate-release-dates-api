package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHolidays

@Service
class BankHolidayApiClient(@Qualifier("bankHolidayApiWebClient") private val webClient: WebClient) {
  private inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}

  fun getBankHolidays(): BankHolidays {
    return webClient.get()
      .uri("/bank-holidays.json")
      .retrieve()
      .bodyToMono(typeReference<BankHolidays>())
      .block()!!
  }
}
