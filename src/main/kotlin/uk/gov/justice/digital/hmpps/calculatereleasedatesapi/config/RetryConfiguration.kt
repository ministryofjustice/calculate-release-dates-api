package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.support.RetryTemplate

@Configuration
class RetryConfiguration {

  @Bean("bulkComparisonRetryTemplate")
  fun bulkComparisonRetryTemplate(): RetryTemplate {
    return RetryTemplate
      .builder()
      .maxAttempts(3)
      .fixedBackoff(250)
      .build()
  }
}
