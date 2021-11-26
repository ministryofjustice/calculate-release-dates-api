package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.stereotype.Component

@Component
object UserContext {
  private val authToken = ThreadLocal<String>()
  fun setAuthToken(aToken: String) {
    authToken.set(aToken)
  }
  fun getAuthToken(): String {
    return authToken.get()
  }
}
