package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.WorkingDay
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isWeekend
import java.time.LocalDate

@Service
class WorkingDayService(
  private val bankHolidayService: BankHolidayService,
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
    log.info("Fetching bank holidays data")
    val bankHolidays = bankHolidayService.getBankHolidays()
      .englandAndWales
      .events
      .map { it.date }

    log.info("Bank holidays data fetched successfully")

    var workingDate = date
    while (workingDate.isWeekend() || bankHolidays.contains(workingDate)) {
      adjustedForWeekend = workingDate.isWeekend() || adjustedForWeekend
      adjustedForBankHoliday = bankHolidays.contains(workingDate) || adjustedForBankHoliday
      workingDate = increment.invoke(workingDate)
    }
    log.info("Next / previous working day determined to be: $workingDate")
    return WorkingDay(workingDate, adjustedForWeekend, adjustedForBankHoliday)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
