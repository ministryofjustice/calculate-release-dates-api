package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import java.time.LocalDate

@Component
class TrancheOne(
  override val trancheConfiguration: SDS40TrancheConfiguration
) : Tranche {

  override fun isBookingApplicableForTrancheCriteria(calculationResult: CalculationResult, bookingSentences: List<CalculableSentence>): Boolean {
    return bookingSentences
      .map { filterAndMapSentencesForNotIncludedTypesByDuration(it, this) }
      .none {
        it >= 5
      }
  }
}
