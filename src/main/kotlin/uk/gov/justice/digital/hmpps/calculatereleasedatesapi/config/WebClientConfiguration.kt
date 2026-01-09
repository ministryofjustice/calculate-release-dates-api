package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient

@Configuration
class WebClientConfiguration(
  @Value("\${prison.api.url}") private val prisonApiUri: String,
  @Value("\${hmpps.auth.url}") private val oauthApiUrl: String,
  @Value("\${bank-holiday.api.url:https://www.gov.uk}") private val bankHolidayApiUrl: String,
  @Value("\${adjustments.api.url}") private val adjustmentsApiUrl: String,
  @Value("\${manage-offences.api.url}") private val manageOffencesApiUrl: String,
  @Value("\${nomis-sync-mapping.api.url}") private val nomisSyncMappingApiUrl: String,
  @Value("\${manage-users.api.url}") private val manageUsersApiUrl: String,
) {

  /*
    TODO This works because we are calling the API with the clients token rather than a system token the UI should be changed to
     use a system token with a username and then we can switch to usernameAwareTokenRequestOAuth2AuthorizedClientManager as per
     https://github.com/ministryofjustice/hmpps-kotlin-lib/blob/main/test-app/src/main/kotlin/uk/gov/justice/digital/hmpps/testapp/config/WebClientConfiguration.kt
   */
  @Bean
  fun prisonApiUserAuthWebClient(webClientBuilder: WebClient.Builder): WebClient = webClientBuilder
    .baseUrl(prisonApiUri)
    .filter(addAuthHeaderFilterFunction())
    .build()

  private fun addAuthHeaderFilterFunction(): ExchangeFilterFunction = ExchangeFilterFunction { request: ClientRequest, next: ExchangeFunction ->
    val filtered = ClientRequest.from(request)
      .header(HttpHeaders.AUTHORIZATION, UserContext.getAuthToken())
      .build()
    next.exchange(filtered)
  }

  @Bean
  fun prisonApiSystemAuthWebClient(
    builder: WebClient.Builder,
    authorizedClientManager: OAuth2AuthorizedClientManager,
  ): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "prison-api", url = prisonApiUri)

  @Bean
  fun prisonApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.baseUrl(prisonApiUri).build()

  @Bean
  fun oauthApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.baseUrl(oauthApiUrl).build()

  @Bean
  fun bankHolidayApiWebClient(builder: WebClient.Builder): WebClient = builder.baseUrl(bankHolidayApiUrl).build()

  @Bean
  fun adjustmentsApiWebClient(
    builder: WebClient.Builder,
    authorizedClientManager: OAuth2AuthorizedClientManager,
  ) = builder.authorisedWebClient(authorizedClientManager, registrationId = "adjustments-api", url = adjustmentsApiUrl)

  @Bean
  fun manageOffencesApiWebClient(
    builder: WebClient.Builder,
    authorizedClientManager: OAuth2AuthorizedClientManager,
  ) = builder.authorisedWebClient(authorizedClientManager, registrationId = "manage-offences-api", url = manageOffencesApiUrl)

  @Bean
  fun nomisSyncMappingApiWebClient(
    builder: WebClient.Builder,
    authorizedClientManager: OAuth2AuthorizedClientManager,
  ) = builder.authorisedWebClient(authorizedClientManager, registrationId = "nomis-sync-mapping-api", url = nomisSyncMappingApiUrl)

  @Bean
  fun manageUsersApiWebClient(
    builder: WebClient.Builder,
    authorizedClientManager: OAuth2AuthorizedClientManager,
  ): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "manage-users-api", url = manageUsersApiUrl)
}
