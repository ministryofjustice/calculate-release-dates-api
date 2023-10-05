package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHoliday
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.RegionBankHolidays
import java.time.LocalDate

class NonFridayReleaseServiceTest {
  private val bankHolidayService = mock<BankHolidayService>()
  private val nonFridayReleaseService = NonFridayReleaseService(bankHolidayService)

  @BeforeEach
  fun reset() {
    reset(bankHolidayService)
    whenever(bankHolidayService.getBankHolidays()).thenReturn(
      BankHolidays(
        RegionBankHolidays(
          "England and Wales",
          listOf(
            BankHoliday("Good Friday", LocalDate.of(2023, 4, 10)),
            BankHoliday("Easter Monday", LocalDate.of(2023, 4, 7)),
            BankHoliday("Early May bank holiday", LocalDate.of(2023, 5, 1)),
          ),
        ),
        RegionBankHolidays("Scotland", emptyList()),
        RegionBankHolidays("Northern Ireland", emptyList()),
      ),
    )
  }

  @Test
  fun `Thursday before good Friday results in Wednesday`() {
    val thursdayBeforeEaster = LocalDate.of(2023, 4, 6)
    val wednesdayBeforeEaster = LocalDate.of(2023, 4, 5)
    val result = nonFridayReleaseService.getDate(thursdayBeforeEaster)

    assertEquals(wednesdayBeforeEaster, result.date)
    assertTrue(result.usePolicy)
  }

  @Test
  fun `Good Friday results in Wednesday`() {
    val goodFriday = LocalDate.of(2023, 4, 10)
    val wednesdayBeforeEaster = LocalDate.of(2023, 4, 5)
    val result = nonFridayReleaseService.getDate(goodFriday)

    assertEquals(wednesdayBeforeEaster, result.date)
    assertTrue(result.usePolicy)
  }

  @Test
  fun `Bank holiday Monday results in previous Thursday`() {
    val bankHolidayMonday = LocalDate.of(2023, 5, 1)
    val thursday = LocalDate.of(2023, 4, 27)
    val result = nonFridayReleaseService.getDate(bankHolidayMonday)

    assertEquals(thursday, result.date)
    assertTrue(result.usePolicy)
  }

  @Test
  fun `Friday results in Thursday`() {
    val friday = LocalDate.of(2023, 10, 6)
    val thursday = LocalDate.of(2023, 10, 5)
    val result = nonFridayReleaseService.getDate(friday)
    assertEquals(thursday, result.date)
    assertTrue(result.usePolicy)
  }

  @Test
  fun `Thursday stays as Thursday`() {
    val thursday = LocalDate.of(2023, 10, 5)
    val result = nonFridayReleaseService.getDate(thursday)
    assertEquals(thursday, result.date)
    assertFalse(result.usePolicy)
  }

  @Test
  fun `weekend results in Thursday`() {
    val sunday = LocalDate.of(2023, 10, 8)
    val thursday = LocalDate.of(2023, 10, 5)
    val result = nonFridayReleaseService.getDate(sunday)
    assertEquals(thursday, result.date)
    assertTrue(result.usePolicy)
  }
}
