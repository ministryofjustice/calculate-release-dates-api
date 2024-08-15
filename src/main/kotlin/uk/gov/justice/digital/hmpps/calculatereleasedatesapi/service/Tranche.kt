package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

interface Tranche {

  fun isBookingApplicableForTrancheCriteria(calculationResult: CalculationResult, booking: Booking): Boolean

  fun filterOnType(
    sentenceToCheck: CalculableSentence,
    trancheCommencementDate: LocalDate,
    trancheTwoCommencementDate: LocalDate? = null,
  ): Boolean {
    if (sentenceToCheck.isRecall()) {
      if (trancheTwoCommencementDate != null) {
        // Recalls are only counted towards tranche classification IF the SLED is on OR after the T2 commencement date
        return sentenceToCheck.sentenceCalculation.adjustedExpiryDate.isAfterOrEqualTo(trancheTwoCommencementDate)
      }
      return sentenceToCheck.sentenceCalculation.adjustedExpiryDate.isAfterOrEqualTo(trancheCommencementDate)
    }

    return !sentenceToCheck.isDto() && !sentenceToCheck.isBotus() && sentenceToCheck !is AFineSentence
  }

  fun filterAndMapSentencesForNotIncludedTypesByDuration(
    sentenceToFilter: CalculableSentence,
    trancheCommencementDate: LocalDate,
    trancheTwoCommencementDate: LocalDate? = null,
  ): Long {
    return if (sentenceToFilter is ConsecutiveSentence) {
      val filteredSentences = sentenceToFilter.orderedSentences
        .filter { filterOnType(it, trancheCommencementDate, trancheTwoCommencementDate) }
      val earliestSentencedAt = filteredSentences.minBy { it.sentencedAt }
      filteredSentences.sumOf { it.getLengthInDays() }.let {
        ChronoUnit.YEARS.between(
          earliestSentencedAt.sentencedAt,
          earliestSentencedAt.sentencedAt.plus(it.toLong(), ChronoUnit.DAYS),
        )
      }
    } else {
      if (filterOnType(sentenceToFilter, trancheCommencementDate, trancheTwoCommencementDate)) {
        ChronoUnit.YEARS.between(
          sentenceToFilter.sentencedAt,
          sentenceToFilter.sentencedAt.plus(sentenceToFilter.getLengthInDays().toLong(), ChronoUnit.DAYS),
        )
      } else {
        0
      }
    }
  }
}
