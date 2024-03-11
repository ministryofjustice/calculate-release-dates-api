package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedCalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateHint
import java.time.LocalDate

@Service
open class CalculationResultEnrichmentService(private val nonFridayReleaseService: NonFridayReleaseService) {

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
    hints += nonFridayReleaseDateAdjustmentHint(type, date)
    return hints.filterNotNull()
  }

  private fun nonFridayReleaseDateAdjustmentHint(type: ReleaseDateType, date: LocalDate): ReleaseDateHint? {
    return if (nonFridayReleaseService.getDate(ReleaseDate(date, type)).usePolicy) ReleaseDateHint(
      "The Discretionary Friday/Pre-Bank Holiday Release Scheme Policy applies to this release date.",
      "https://www.gov.uk/government/publications/discretionary-fridaypre-bank-holiday-release-scheme-policy-framework",
    ) else null
  }

}