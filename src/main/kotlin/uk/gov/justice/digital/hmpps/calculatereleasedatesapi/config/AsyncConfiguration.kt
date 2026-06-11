package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor

@EnableAsync
@Configuration
class AsyncConfiguration {

  @Bean(name = ["threadPoolTaskExecutor"])
  fun threadPoolTaskExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor()

  @Bean
  fun taskExecutor(delegate: ThreadPoolTaskExecutor): DelegatingSecurityContextAsyncTaskExecutor = DelegatingSecurityContextAsyncTaskExecutor(delegate)
}
