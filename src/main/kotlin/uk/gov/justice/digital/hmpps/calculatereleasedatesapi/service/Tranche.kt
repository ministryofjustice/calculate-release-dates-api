package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

interface Tranche {

  fun isBookingApplicableForTrancheCriteria(calculationResult: CalculationResult, bookingSentences: List<CalculableSentence>): Boolean

  fun filterOnType(
    sentenceToCheck: CalculableSentence,
    trancheCommencementDate: LocalDate,
    trancheTwoCommencementDate: LocalDate? = null,
  ): Boolean {
    if (!sentenceToCheck.isDto() && !sentenceToCheck.isBotus() && sentenceToCheck !is AFineSentence) {
      return sentenceToCheck.sentenceCalculation.adjustedExpiryDate.isAfterOrEqualTo(trancheCommencementDate)
    }
    return false
  }

  fun filterAndMapSentencesForNotIncludedTypesByDuration(
    sentenceToFilter: CalculableSentence,
    trancheCommencementDate: LocalDate,
    trancheTwoCommencementDate: LocalDate? = null,
  ): Long {
    return if (sentenceToFilter is ConsecutiveSentence) {
      val filteredSentences = sentenceToFilter.orderedSentences
        .filter { filterOnType(it, trancheCommencementDate, trancheTwoCommencementDate) }

      if (filteredSentences.any()) {
        val earliestSentencedAt = filteredSentences.minBy { it.sentencedAt }
        var concurrentSentenceEndDate = earliestSentencedAt.sentencedAt

        // Iterate over the sentences to update the concurrentSentenceEndDate
        filteredSentences.forEach { sentence ->
          // Update the concurrent end date by calculating the new end date from the current end date
          concurrentSentenceEndDate = sentence.totalDuration().getEndDate(concurrentSentenceEndDate).plusDays(1)
        }

        // Calculate the difference in years between the earliest sentenced date and the final concurrentSentenceEndDate
        val yearsBetween = ChronoUnit.YEARS.between(
          earliestSentencedAt.sentencedAt,
          concurrentSentenceEndDate,
        )

        yearsBetween
      } else {
        0
      }
    } else {
      if (filterOnType(sentenceToFilter, trancheCommencementDate, trancheTwoCommencementDate)) {
        ChronoUnit.YEARS.between(
          sentenceToFilter.sentencedAt,
          sentenceToFilter.sentencedAt.plus(sentenceToFilter.getLengthInDays().toLong(), ChronoUnit.DAYS).plusDays(1),
        )
      } else {
        0
      }
    }
  }
}
