package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfiguration(
  @Value("\${prison.api.url}") private val prisonApiUri: String,
  @Value("\${hmpps.auth.url}") private val oauthApiUrl: String,
  @Value("\${bank-holiday.api.url:https://www.gov.uk}") private val bankHolidayApiUrl: String,
  private val webClientBuilder: WebClient.Builder,
) {

  @Bean
  fun prisonApiWebClient(): WebClient {
    return webClientBuilder
      .baseUrl(prisonApiUri)
      .filter(addAuthHeaderFilterFunction())
      .build()
  }

  private fun addAuthHeaderFilterFunction(): ExchangeFilterFunction {
    return ExchangeFilterFunction { request: ClientRequest, next: ExchangeFunction ->
      val filtered = ClientRequest.from(request)
        .header(HttpHeaders.AUTHORIZATION, UserContext.getAuthToken())
        .build()
      next.exchange(filtered)
    }
  }

  @Bean
  fun prisonApiHealthWebClient(): WebClient {
    return webClientBuilder.baseUrl(prisonApiUri).build()
  }

  @Bean
  fun oauthApiHealthWebClient(): WebClient {
    return webClientBuilder.baseUrl(oauthApiUrl).build()
  }

  @Bean
  fun bankHolidayApiWebClient(): WebClient {
    return webClientBuilder.baseUrl(bankHolidayApiUrl).build()
  }
}
