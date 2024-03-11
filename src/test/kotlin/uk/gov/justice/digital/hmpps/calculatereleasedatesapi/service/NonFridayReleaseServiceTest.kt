package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHoliday
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.RegionBankHolidays
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class NonFridayReleaseServiceTest {
  companion object {
    private val aDayBeforeTheConfiguredHolidays = LocalDate.of(2023, 4, 1)
  }
  private val bankHolidayService = mock<BankHolidayService>()

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
    val result = nonFridayReleaseService(aDayBeforeTheConfiguredHolidays).getDate(thursdayBeforeEaster)

    assertEquals(wednesdayBeforeEaster, result.date)
    assertTrue(result.usePolicy)
  }

  @Test
  fun `Good Friday results in Wednesday`() {
    val goodFriday = LocalDate.of(2023, 4, 10)
    val wednesdayBeforeEaster = LocalDate.of(2023, 4, 5)
    val result = nonFridayReleaseService(aDayBeforeTheConfiguredHolidays).getDate(goodFriday)

    assertEquals(wednesdayBeforeEaster, result.date)
    assertTrue(result.usePolicy)
  }

  @Test
  fun `Bank holiday Monday results in previous Thursday`() {
    val bankHolidayMonday = LocalDate.of(2023, 5, 1)
    val thursday = LocalDate.of(2023, 4, 27)
    val result = nonFridayReleaseService(aDayBeforeTheConfiguredHolidays).getDate(bankHolidayMonday)

    assertEquals(thursday, result.date)
    assertTrue(result.usePolicy)
  }

  @Test
  fun `Friday results in Thursday`() {
    val friday = LocalDate.of(2023, 10, 6)
    val thursday = LocalDate.of(2023, 10, 5)
    val result = nonFridayReleaseService(aDayBeforeTheConfiguredHolidays).getDate(friday)
    assertEquals(thursday, result.date)
    assertTrue(result.usePolicy)
  }

  @Test
  fun `Thursday stays as Thursday`() {
    val thursday = LocalDate.of(2023, 10, 5)
    val result = nonFridayReleaseService(aDayBeforeTheConfiguredHolidays).getDate(thursday)
    assertEquals(thursday, result.date)
    assertFalse(result.usePolicy)
  }

  @Test
  fun `weekend results in Thursday`() {
    val sunday = LocalDate.of(2023, 10, 8)
    val thursday = LocalDate.of(2023, 10, 5)
    val result = nonFridayReleaseService(aDayBeforeTheConfiguredHolidays).getDate(sunday)
    assertEquals(thursday, result.date)
    assertTrue(result.usePolicy)
  }

  @ParameterizedTest
  @CsvSource(
    "CRD,true",
    "LED,false",
  )
  fun `only apply rule for relevant release date type`(type: ReleaseDateType, expected: Boolean) {
    val sunday = LocalDate.of(2023, 10, 8)
    val result = nonFridayReleaseService(aDayBeforeTheConfiguredHolidays).getDate(ReleaseDate(sunday, type))
    assertThat(result.usePolicy).isEqualTo(expected)
  }

  @Test
  fun `must be a future date to consider it`() {
    val today = LocalDate.of(2023, 10, 9)
    val sundayWhereTheFridayBeforeIsABankHoliday = LocalDate.of(2023, 10, 8)
    val result = nonFridayReleaseService(today).getDate(sundayWhereTheFridayBeforeIsABankHoliday)
    assertEquals(sundayWhereTheFridayBeforeIsABankHoliday, result.date)
    assertFalse(result.usePolicy)
  }

  private fun nonFridayReleaseService(today: LocalDate): NonFridayReleaseService {
    val clock = Clock.fixed(today.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
    return NonFridayReleaseService(bankHolidayService, clock)
  }
}
