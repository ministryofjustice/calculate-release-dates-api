package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfiguration(
  @Value("\${hmpps.auth.url}") private val oauthApiUrl: String,
  private val webClientBuilder: WebClient.Builder
) {

  @Bean
  fun oauthApiHealthWebClient(): WebClient {
    return webClientBuilder.baseUrl(oauthApiUrl).build()
  }
}
