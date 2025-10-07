package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
private class ResourceServerConfiguration {
  @Bean
  fun web(http: HttpSecurity): SecurityFilterChain {
    http {
      sessionManagement {
        sessionCreationPolicy = SessionCreationPolicy.STATELESS
      }
      csrf { disable() }
      headers {
        referrerPolicy {
          policy = ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN
        }
      }
      authorizeHttpRequests {
        authorize(HttpMethod.GET, "/webjars/**", permitAll)
        authorize(HttpMethod.GET, "/favicon.ico", permitAll)
        authorize(HttpMethod.GET, "/health/**", permitAll)
        authorize(HttpMethod.GET, "/info", permitAll)
        authorize(HttpMethod.GET, "/swagger-resources/**", permitAll)
        authorize(HttpMethod.GET, "/v3/api-docs/**", permitAll)
        authorize(HttpMethod.GET, "/swagger-ui/**", permitAll)
        authorize(HttpMethod.GET, "/swagger-ui.html", permitAll)
        authorize(HttpMethod.POST, "/h2-console/**", permitAll)
        authorize(HttpMethod.GET, "/some-url-not-found", permitAll)
        authorize(HttpMethod.PUT, "/queue-admin/retry-all-dlqs", permitAll)
        authorize(anyRequest, authenticated)
      }
      oauth2ResourceServer {
        jwt {
          jwtAuthenticationConverter = AuthAwareTokenConverter()
        }
      }
    }
    return http.build()
  }
}
