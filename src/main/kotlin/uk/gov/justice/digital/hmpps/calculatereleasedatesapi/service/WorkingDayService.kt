package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.WorkingDay
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isWeekend
import java.time.LocalDate

@Service
class WorkingDayService(
  private val bankHolidayApiClient: BankHolidayApiClient
) {
  fun nextWorkingDay(date: LocalDate): WorkingDay {
    return iterateOverNonWorkingDays(date) { it.plusDays(1) }
  }

  fun previousWorkingDay(date: LocalDate): WorkingDay {
    return iterateOverNonWorkingDays(date) { it.minusDays(1) }
  }

  private fun iterateOverNonWorkingDays(date: LocalDate, increment: (LocalDate) -> LocalDate): WorkingDay {
    var adjustedForWeekend = false
    var adjustedForBankHoliday = false
    val bankHolidays = bankHolidayApiClient.getBankHolidays()
      .englandAndWales
      .events
      .map { it.date }

    var workingDate = date
    while (workingDate.isWeekend() || bankHolidays.contains(workingDate)) {
      adjustedForWeekend = workingDate.isWeekend() || adjustedForWeekend
      adjustedForBankHoliday = bankHolidays.contains(workingDate) || adjustedForBankHoliday
      workingDate = increment.invoke(workingDate)
    }
    return WorkingDay(workingDate, adjustedForWeekend, adjustedForBankHoliday)
  }
}
