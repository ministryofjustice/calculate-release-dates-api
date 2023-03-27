package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BankHolidayCacheSchedule(
  val bankHolidayService: BankHolidayService
) {

  // Every 5 mins from 03:00 till 03:59 Monday to Friday.
  @Scheduled(cron = "\${random.int[0,59]} */5 3 * * MON-FRI")
  fun refreshCache() {
    log.info("Schedule starting to refresh bank holiday cache")
    bankHolidayService.refreshCache()
    log.info("Schedule finished")
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
