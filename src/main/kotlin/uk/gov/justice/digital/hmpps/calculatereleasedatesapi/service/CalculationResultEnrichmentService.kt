package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedCalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateHint
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class CalculationResultEnrichmentService(private val nonFridayReleaseService: NonFridayReleaseService, val workingDayService: WorkingDayService, val clock: Clock) {
  companion object {
    private val typesAllowedWeekendAdjustment = listOf(
      ReleaseDateType.CRD,
      ReleaseDateType.ARD,
      ReleaseDateType.PRRD,
      ReleaseDateType.HDCED,
      ReleaseDateType.PED,
      ReleaseDateType.ETD,
      ReleaseDateType.MTD,
      ReleaseDateType.LTD,
    )
  }

  fun addDetailToCalculationResults(calculationRequest: CalculationRequest): DetailedCalculationResults {
    return DetailedCalculationResults(
      calculationRequest.id,
      calculationRequest.calculationOutcomes
        .filter { it.outcomeDate != null }
        .map { ReleaseDateType.valueOf(it.calculationDateType) to it.outcomeDate!! }
        .associateBy(
          { (type, _) -> type },
          { (type, date) -> DetailedReleaseDate(type, type.fullName, date, getHints(type, date)) },
        ),
    )
  }

  private fun getHints(type: ReleaseDateType, date: LocalDate): List<ReleaseDateHint> {
    val hints = mutableListOf<ReleaseDateHint?>()
    hints += nonFridayReleaseDateOrWeekendAdjustmentHintOrNull(type, date)
    return hints.filterNotNull()
  }

  private fun nonFridayReleaseDateOrWeekendAdjustmentHintOrNull(type: ReleaseDateType, date: LocalDate): ReleaseDateHint? {
    return if (nonFridayReleaseService.getDate(ReleaseDate(date, type)).usePolicy) {
      ReleaseDateHint(
        "The Discretionary Friday/Pre-Bank Holiday Release Scheme Policy applies to this release date.",
        "https://www.gov.uk/government/publications/discretionary-fridaypre-bank-holiday-release-scheme-policy-framework",
      )
    } else {
      weekendAdjustmentHintOrNull(type, date)
    }
  }

  private fun weekendAdjustmentHintOrNull(type: ReleaseDateType, date: LocalDate): ReleaseDateHint? {
    if(type !in typesAllowedWeekendAdjustment || date.isBefore(LocalDate.now(clock))) {
      return null
    }
    val previousWorkingDay = workingDayService.previousWorkingDay(date)
    return if (previousWorkingDay.date != date) {
      ReleaseDateHint("${previousWorkingDay.date.format(DateTimeFormatter.ofPattern("cccc, dd LLLL yyyy"))} when adjusted to a working day")
    } else {
      null
    }
  }

}