package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CacheConfiguration.Companion.BANK_HOLIDAYS_CACHE_NAME
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.WorkingDay
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHolidays
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class WorkingDayIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var cacheManager: CacheManager

  @Test
  fun `nextWorkingDay test weekend adjustment`() {
    val result = makeApiCall("/working-day/next/$SATURDAY")

    assertEquals(MONDAY, result.date)
    assertTrue(result.adjustedForWeekend)
    assertFalse(result.adjustedForBankHoliday)
  }

  @Test
  fun `previousWorkingDay test weekend adjustment and check that the cache is populated`() {
    val result = makeApiCall("/working-day/previous/$SATURDAY")
    @Suppress("UNCHECKED_CAST") val nativeCache: ConcurrentHashMap<Any, Any> =
      cacheManager.getCache(BANK_HOLIDAYS_CACHE_NAME)!!.nativeCache as ConcurrentHashMap<Any, Any>
    val holidays: BankHolidays = nativeCache.values.first() as BankHolidays

    assertEquals(FRIDAY, result.date)
    assertTrue(result.adjustedForWeekend)
    assertFalse(result.adjustedForBankHoliday)
    assertEquals(1, nativeCache.size)
    assertTrue(holidays.englandAndWales.events.isNotEmpty())
  }

  private fun makeApiCall(uri: String) = webTestClient.get()
    .uri(uri)
    .accept(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(WorkingDay::class.java)
    .returnResult().responseBody!!

  companion object {
    val SATURDAY: LocalDate = LocalDate.of(2021, 10, 23)
    val MONDAY: LocalDate = LocalDate.of(2021, 10, 25)
    val FRIDAY: LocalDate = LocalDate.of(2021, 10, 22)
  }
}
