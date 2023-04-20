package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.BankHolidayCache
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHoliday
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.RegionBankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.BankHolidayCacheRepository
import java.time.LocalDate
import java.time.LocalDateTime

class BankHolidayServiceTest : IntegrationTestBase() {

  @Autowired
  lateinit var bankHolidayService: BankHolidayService

  @Autowired
  lateinit var bankHolidayCacheRepository: BankHolidayCacheRepository

  @Autowired
  lateinit var objectMapper: ObjectMapper

  @Test
  fun `Test refreshing cache will create an entry in the cache table for the first time`() {
    bankHolidayService.refreshCache()

    val cache = bankHolidayCacheRepository.findFirstByOrderByIdAsc()

    assertThat(cache).isNotNull
    assertThat(cache!!.cachedAt.toLocalDate()).isEqualTo(LocalDate.now())
    assertThat(cache.data).isNotNull
  }

  @Test
  fun `Test refreshing cache will update an entry in the cache table`() {
    bankHolidayCacheRepository.save(BankHolidayCache(cachedAt = LocalDateTime.now().minusDays(10), data = objectToJson(cachedBankHolidays, objectMapper)))

    bankHolidayService.refreshCache()

    val cache = bankHolidayCacheRepository.findFirstByOrderByIdAsc()

    assertThat(cache).isNotNull
    assertThat(cache!!.cachedAt.toLocalDate()).isEqualTo(LocalDate.now())
    assertThat(cache.data).isNotNull
  }

  @Test
  fun `Test getting bank holidays will go to API if cache is empty`() {
    val bankHolidays = bankHolidayService.getBankHolidays()

    assertThat(bankHolidays.englandAndWales.events).isNotEmpty

    assertThat(bankHolidayCacheRepository.findFirstByOrderByIdAsc()).isNotNull
  }

  @Test
  fun `Test getting bank holidays will go to cache if present`() {
    bankHolidayCacheRepository.save(BankHolidayCache(cachedAt = LocalDateTime.now().minusDays(10), data = objectToJson(cachedBankHolidays, objectMapper)))

    val bankHolidays = bankHolidayService.getBankHolidays()

    assertThat(bankHolidays).isEqualTo(cachedBankHolidays)
  }

  companion object {
    val cachedBankHolidays =
      BankHolidays(
        RegionBankHolidays(
          "England and Wales",
          listOf(
            BankHoliday("Christmas Day Bank Holiday", LocalDate.of(2021, 12, 27)),
            BankHoliday("Boxing Day Bank Holiday", LocalDate.of(2021, 12, 28)),
          ),
        ),
        RegionBankHolidays("Scotland", emptyList()),
        RegionBankHolidays("Northern Ireland", emptyList()),
      )
  }
}
