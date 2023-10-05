package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NonFridayReleaseDay
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isNonWorkingDay
import java.time.LocalDate

@Service
class NonFridayReleaseService(
  private val bankHolidayService: BankHolidayService,
) {
  fun getDate(date: LocalDate): NonFridayReleaseDay {
    var usePolicy = false
    val bankHolidays = bankHolidayService.getBankHolidays()
      .englandAndWales
      .events
      .map { it.date }

    var nonFridayReleaseDate = date
    while (nonFridayReleaseDate.isNonWorkingDay(bankHolidays) || nonFridayReleaseDate.plusDays(1L).isNonWorkingDay(bankHolidays)) {
      usePolicy = true
      nonFridayReleaseDate = nonFridayReleaseDate.minusDays(1L)
    }
    return NonFridayReleaseDay(nonFridayReleaseDate, usePolicy)
  }
}
