package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageusers.UserDetails

@Component
class ManageUsersApiClient(@param:Qualifier("manageUsersApiWebClient") private val manageUsersApiWebClient: WebClient) {

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getUserByUsername(username: String): UserDetails? = manageUsersApiWebClient
    .get()
    .uri("/users/{username}", username)
    .retrieve()
    .bodyToMono(UserDetails::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      log.debug("Couldn't find user with username: {}", username)
      Mono.empty()
    }
    .block()
}
