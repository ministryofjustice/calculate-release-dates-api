package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ClockConfiguration {
  @Bean
  fun defaultClock() : Clock  = Clock.systemDefaultZone()
}