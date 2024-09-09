package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import java.time.LocalDate

@Component
class TrancheTwo(
  @Value("\${sds-early-release-tranches.tranche-two-date}")
  @DateTimeFormat(pattern = "yyyy-MM-dd") val trancheCommencementDate: LocalDate,
) : Tranche {

  override fun isBookingApplicableForTrancheCriteria(calculationResult: CalculationResult, bookingSentences: List<CalculableSentence>): Boolean {
    return (
      bookingSentences
        .filter { it.sentencedAt.isBefore(trancheCommencementDate) }
        .map { filterAndMapSentencesForNotIncludedTypesByDuration(it, trancheCommencementDate, trancheCommencementDate) }
        .any {
          it >= 5
        }
      )
  }
}
