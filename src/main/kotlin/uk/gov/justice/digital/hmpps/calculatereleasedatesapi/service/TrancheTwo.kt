package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Component
class TrancheTwo(
  @Value("\${sds-early-release-tranches.tranche-two-date}")
  @DateTimeFormat(pattern = "yyyy-MM-dd") val trancheCommencementDate: LocalDate,
) : Tranche {

  override fun isBookingApplicableForTrancheCriteria(calculationResult: CalculationResult, booking: Booking): Boolean {
    return (
      booking.consecutiveSentences.any { consecutiveSentence ->
        consecutiveSentence.durationIsGreaterThan(
          5,
          ChronoUnit.YEARS,
        )
      } ||
        booking.sentences.any {
          it.durationIsGreaterThan(5, ChronoUnit.YEARS)
        }
      )
  }
}
