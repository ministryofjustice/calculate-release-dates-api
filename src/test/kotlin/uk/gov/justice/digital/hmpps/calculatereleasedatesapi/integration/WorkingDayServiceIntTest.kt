package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CacheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.WorkingDayService
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class WorkingDayServiceIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var workingDayService: WorkingDayService

  @Autowired
  lateinit var cacheManager: CacheManager

  @Test
  fun `nextWorkingDay test weekend adjustment`() {
    val saturday = LocalDate.of(2021, 10, 23)
    val monday = LocalDate.of(2021, 10, 25)

    val nextWorkingDay = workingDayService.nextWorkingDay(saturday)

    assertEquals(monday, nextWorkingDay)
  }

  @Test
  fun `nextWorkingDay test bank holiday adjustment`() {
    val christmasBankHoliday = LocalDate.of(2021, 12, 27)
    val afterBoxingDayHoliday = LocalDate.of(2021, 12, 29)

    val nextWorkingDay = workingDayService.nextWorkingDay(christmasBankHoliday)

    assertEquals(afterBoxingDayHoliday, nextWorkingDay)
  }

  @Test
  fun `previousWorkingDay test weekend adjustment`() {
    val saturday = LocalDate.of(2021, 10, 23)
    val friday = LocalDate.of(2021, 10, 22)

    val nextWorkingDay = workingDayService.previousWorkingDay(saturday)

    assertEquals(friday, nextWorkingDay)
  }

  @Test
  fun `previousWorkingDay test bank holiday adjustment`() {
    val christmasBankHoliday = LocalDate.of(2021, 12, 27)
    val christmasEve = LocalDate.of(2021, 12, 24)

    val nextWorkingDay = workingDayService.previousWorkingDay(christmasBankHoliday)

    assertEquals(christmasEve, nextWorkingDay)
  }

  @Test
  fun `fetching bank holidays enters the cache`() {
    workingDayService.previousWorkingDay(LocalDate.now())

    @Suppress("UNCHECKED_CAST") val nativeCache: ConcurrentHashMap<Any, Any> =
      cacheManager.getCache(CacheConfiguration.BANK_HOLIDAYS_CACHE_NAME)!!.nativeCache as ConcurrentHashMap<Any, Any>

    assertEquals(1, nativeCache.size)
  }
}
