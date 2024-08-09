package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Suppress("ktlint:standard:filename")
interface Tranche {

  fun isBookingApplicableForTrancheCriteria(calculationResult: CalculationResult, booking: Booking): Boolean

  fun filterOnType(sentenceToCheck: CalculableSentence, trancheCommencementDate: LocalDate): Boolean {
    if (sentenceToCheck.isRecall()) {
      return sentenceToCheck.sentenceCalculation.adjustedExpiryDate.isAfterOrEqualTo(trancheCommencementDate)
    }

    return !sentenceToCheck.isDto() && !sentenceToCheck.isBotus() && sentenceToCheck !is AFineSentence
  }

  fun filterAndMapSentencesForNotIncludedTypesByDuration(sentenceToFilter: CalculableSentence, trancheCommencementDate: LocalDate): Long {
    return if (sentenceToFilter is ConsecutiveSentence) {
      val filteredSentences = sentenceToFilter.orderedSentences.filter { filterOnType(it, trancheCommencementDate) }
      val earliestSentencedAt = filteredSentences.minBy { it.sentencedAt }
      filteredSentences.sumOf { it.getLengthInDays() }.let {
        ChronoUnit.YEARS.between(
          earliestSentencedAt.sentencedAt,
          earliestSentencedAt.sentencedAt.plus(it.toLong(), ChronoUnit.DAYS),
        )
      }
    } else {
      if (filterOnType(sentenceToFilter, trancheCommencementDate)) {
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
