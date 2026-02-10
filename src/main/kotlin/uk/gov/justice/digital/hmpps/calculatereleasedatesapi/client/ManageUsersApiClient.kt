package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageusersapi.model.PrisonUserBasicDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageusers.UserDetails

@Component
class ManageUsersApiClient(@param:Qualifier("manageUsersApiWebClient") private val manageUsersApiWebClient: WebClient) {

  private inline fun <reified T : Any> typeReference() = object : ParameterizedTypeReference<T>() {}

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

  fun getUsersByUsernames(usernames: Set<String>): Map<String, PrisonUserBasicDetails>? = manageUsersApiWebClient
    .post()
    .uri("/prisonusers/find-by-usernames")
    .bodyValue(usernames) // Pass the list of usernames as the body
    .retrieve()
    .bodyToMono(typeReference<Map<String, PrisonUserBasicDetails>>())
    .onErrorResume { e ->
      if (e is WebClientResponseException.NotFound) {
        log.debug("Couldn't find usersDetails: {}", usernames)
        Mono.empty()
      } else {
        Mono.error(e)
      }
    }
    .block()
}
