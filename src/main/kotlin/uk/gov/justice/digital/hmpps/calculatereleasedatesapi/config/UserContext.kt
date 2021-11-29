package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.stereotype.Component

@Component
object UserContext {
  private val authToken = ThreadLocal<String>()
  fun setAuthToken(token: String?) = authToken.set(token)
  fun getAuthToken(): String = authToken.get()
}
