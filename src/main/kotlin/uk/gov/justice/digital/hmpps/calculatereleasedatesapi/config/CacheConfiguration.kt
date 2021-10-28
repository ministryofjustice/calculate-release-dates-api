package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
@EnableScheduling
class CacheConfiguration {

  @Bean
  fun cacheManager(): CacheManager {
    return ConcurrentMapCacheManager(BANK_HOLIDAYS_CACHE_NAME)
  }

  @CacheEvict(allEntries = true, cacheNames = [BANK_HOLIDAYS_CACHE_NAME])
  @Scheduled(fixedDelay = TTL_HOURS, timeUnit = TimeUnit.HOURS)
  fun cacheEvict() {
    log.info("Evicting cache $BANK_HOLIDAYS_CACHE_NAME")
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val BANK_HOLIDAYS_CACHE_NAME: String = "bankHolidays"
    const val TTL_HOURS: Long = 1
  }
}
