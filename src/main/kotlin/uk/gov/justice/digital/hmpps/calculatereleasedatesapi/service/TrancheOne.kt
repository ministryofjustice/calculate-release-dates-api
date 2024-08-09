package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import java.time.LocalDate

@Component
class TrancheOne(
  @Value("\${sds-early-release-tranches.tranche-one-date}")
  @DateTimeFormat(pattern = "yyyy-MM-dd") val trancheCommencementDate: LocalDate,
) : Tranche {

  override fun isBookingApplicableForTrancheCriteria(calculationResult: CalculationResult, booking: Booking): Boolean {
    return booking.getAllExtractableSentences()
      .map { filterAndMapSentencesForNotIncludedTypesByDuration(it, trancheCommencementDate) }
      .none {
        it >= 5
      }
  }
}
