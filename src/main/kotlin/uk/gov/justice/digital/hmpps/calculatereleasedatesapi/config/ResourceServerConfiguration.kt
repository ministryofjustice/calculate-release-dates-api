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
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

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
        authorize(AntPathRequestMatcher("/webjars/**", HttpMethod.GET.name()), permitAll)
        authorize(AntPathRequestMatcher("favicon.ico", HttpMethod.GET.name()), permitAll)
        authorize(AntPathRequestMatcher("/health/**", HttpMethod.GET.name()), permitAll)
        authorize(AntPathRequestMatcher("/info", HttpMethod.GET.name()), permitAll)
        authorize(AntPathRequestMatcher("/swagger-resources/**", HttpMethod.GET.name()), permitAll)
        authorize(AntPathRequestMatcher("/v3/api-docs/**", HttpMethod.GET.name()), permitAll)
        authorize(AntPathRequestMatcher("/swagger-ui/**", HttpMethod.GET.name()), permitAll)
        authorize(AntPathRequestMatcher("/swagger-ui.html", HttpMethod.GET.name()), permitAll)
        authorize(AntPathRequestMatcher("/h2-console/**", HttpMethod.POST.name()), permitAll)
        authorize(AntPathRequestMatcher("/some-url-not-found", HttpMethod.GET.name()), permitAll)
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
