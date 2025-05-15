package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.apache.commons.lang3.RegExUtils
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.AuthAwareAuthenticationToken
import java.util.stream.Collectors

@Service
class ServiceUserService {

  fun getCurrentAuthentication(): AuthAwareAuthenticationToken = SecurityContextHolder.getContext().authentication as AuthAwareAuthenticationToken?
    ?: throw IllegalStateException("User is not authenticated")

  fun getUsername(): String = getCurrentAuthentication().principal

  fun hasRoles(allowedRoles: List<String>): Boolean {
    val roles = allowedRoles.stream()
      .map { r: String? -> RegExUtils.replaceFirst(r, "ROLE_", "") }
      .collect(Collectors.toList())
    return hasMatchingRole(roles, getCurrentAuthentication())
  }

  private fun hasMatchingRole(roles: List<String>, authentication: Authentication?): Boolean = authentication != null &&
    authentication.authorities.stream()
      .anyMatch { a: GrantedAuthority? -> roles.contains(RegExUtils.replaceFirst(a!!.authority, "ROLE_", "")) }
}
