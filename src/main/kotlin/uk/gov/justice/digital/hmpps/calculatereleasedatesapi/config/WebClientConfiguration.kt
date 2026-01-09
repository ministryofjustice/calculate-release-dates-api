package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.web.context.annotation.RequestScope
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.usernameAwareTokenRequestOAuth2AuthorizedClientManager

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

  @Bean
  @RequestScope
  fun prisonApiUserAuthWebClient(
    clientRegistrationRepository: ClientRegistrationRepository,
    oAuth2AuthorizedClientService: OAuth2AuthorizedClientService,
    builder: WebClient.Builder,
  ): WebClient = builder.authorisedWebClient(
    usernameAwareTokenRequestOAuth2AuthorizedClientManager(
      clientRegistrationRepository,
      oAuth2AuthorizedClientService,
    ),
    registrationId = "prison-api",
    url = prisonApiUri,
  )

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
