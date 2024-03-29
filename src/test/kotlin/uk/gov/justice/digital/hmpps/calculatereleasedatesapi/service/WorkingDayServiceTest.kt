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

class WorkingDayServiceTest {
  private val bankHolidayService = mock<BankHolidayService>()
  private val workingDayService = WorkingDayService(bankHolidayService)

  @BeforeEach
  fun reset() {
    reset(bankHolidayService)
    whenever(bankHolidayService.getBankHolidays()).thenReturn(
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
      ),
    )
  }

  @Test
  fun `nextWorkingDay test weekend adjustment`() {
    val saturday = LocalDate.of(2021, 10, 23)
    val monday = LocalDate.of(2021, 10, 25)

    val nextWorkingDay = workingDayService.nextWorkingDay(saturday)

    assertEquals(monday, nextWorkingDay.date)
    assertTrue(nextWorkingDay.adjustedForWeekend)
    assertFalse(nextWorkingDay.adjustedForBankHoliday)
  }

  @Test
  fun `nextWorkingDay test bank holiday adjustment`() {
    val christmasBankHoliday = LocalDate.of(2021, 12, 27)
    val afterBoxingDayHoliday = LocalDate.of(2021, 12, 29)

    val nextWorkingDay = workingDayService.nextWorkingDay(christmasBankHoliday)

    assertEquals(afterBoxingDayHoliday, nextWorkingDay.date)
    assertFalse(nextWorkingDay.adjustedForWeekend)
    assertTrue(nextWorkingDay.adjustedForBankHoliday)
  }

  @Test
  fun `previousWorkingDay test weekend adjustment`() {
    val saturday = LocalDate.of(2021, 10, 23)
    val friday = LocalDate.of(2021, 10, 22)

    val nextWorkingDay = workingDayService.previousWorkingDay(saturday)

    assertEquals(friday, nextWorkingDay.date)
    assertTrue(nextWorkingDay.adjustedForWeekend)
    assertFalse(nextWorkingDay.adjustedForBankHoliday)
  }

  @Test
  fun `previousWorkingDay test bank holiday adjustment`() {
    val christmasBankHoliday = LocalDate.of(2021, 12, 27)
    val christmasEve = LocalDate.of(2021, 12, 24)

    val nextWorkingDay = workingDayService.previousWorkingDay(christmasBankHoliday)

    assertEquals(christmasEve, nextWorkingDay.date)
    assertTrue(nextWorkingDay.adjustedForWeekend)
    assertTrue(nextWorkingDay.adjustedForBankHoliday)
  }
}
