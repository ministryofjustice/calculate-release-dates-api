package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NonFridayReleaseDay
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isNonWorkingDay
import java.time.Clock
import java.time.LocalDate

@Service
class NonFridayReleaseService(
  private val bankHolidayService: BankHolidayService,
  private val clock: Clock,
) {

  companion object {
    private val supportedTypes = setOf(ReleaseDateType.CRD, ReleaseDateType.ARD, ReleaseDateType.PRRD, ReleaseDateType.ETD, ReleaseDateType.MTD, ReleaseDateType.LTD)
  }

  fun getDate(date: ReleaseDate): NonFridayReleaseDay = if (date.type in supportedTypes) getDate(date.date) else NonFridayReleaseDay(date.date, false)

  fun getDate(date: LocalDate): NonFridayReleaseDay {
    if (date.isBefore(LocalDate.now(clock))) {
      return NonFridayReleaseDay(date, false)
    }
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
