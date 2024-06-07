package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfiguration(
  @Value("\${prison.api.url}") private val prisonApiUri: String,
  @Value("\${hmpps.auth.url}") private val oauthApiUrl: String,
  @Value("\${bank-holiday.api.url:https://www.gov.uk}") private val bankHolidayApiUrl: String,
  @Value("\${adjustments.api.url}") private val adjustmentsApiUrl: String,
  @Value("\${manage-offences.api.url}") private val manageOffencesApiUrl: String,
) {

  @Bean
  fun prisonApiWebClient(webClientBuilder: WebClient.Builder): WebClient {
    return webClientBuilder
      .baseUrl(prisonApiUri)
      .filter(addAuthHeaderFilterFunction())
      .build()
  }

  @Bean
  fun prisonApiBulkComparisonWebClient(
    builder: WebClient.Builder,
    authorizedClientManager: OAuth2AuthorizedClientManager,
  ): WebClient {
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("prison-api")
    return builder
      .baseUrl(prisonApiUri)
      .apply(oauth2Client.oauth2Configuration())
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
  fun prisonApiHealthWebClient(webClientBuilder: WebClient.Builder): WebClient {
    return webClientBuilder.baseUrl(prisonApiUri).build()
  }

  @Bean
  fun oauthApiHealthWebClient(webClientBuilder: WebClient.Builder): WebClient {
    return webClientBuilder.baseUrl(oauthApiUrl).build()
  }

  @Bean
  fun bankHolidayApiWebClient(webClientBuilder: WebClient.Builder): WebClient {
    return webClientBuilder.baseUrl(bankHolidayApiUrl).build()
  }

  @Bean
  fun adjustmentsApiWebClient(webClientBuilder: WebClient.Builder): WebClient {
    return webClientBuilder
      .baseUrl(adjustmentsApiUrl)
      .filter(addAuthHeaderFilterFunction())
      .build()
  }

  @Bean
  fun manageOffencesApiWebClient(
    builder: WebClient.Builder,
    authorizedClientManager: OAuth2AuthorizedClientManager,
  ): WebClient {
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("manage-offences-api")
    return builder
      .baseUrl(manageOffencesApiUrl)
      .apply(oauth2Client.oauth2Configuration())
      .build()
  }

  @Bean
  fun authorizedClientManager(
    clientRegistrationRepository: ClientRegistrationRepository?,
    oAuth2AuthorizedClientService: OAuth2AuthorizedClientService?,
  ): OAuth2AuthorizedClientManager? {
    val authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build()
    val authorizedClientManager = AuthorizedClientServiceOAuth2AuthorizedClientManager(
      clientRegistrationRepository,
      oAuth2AuthorizedClientService,
    )
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider)
    return authorizedClientManager
  }
}
