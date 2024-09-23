package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.temporal.ChronoUnit

interface Tranche {

  val trancheConfiguration: SDS40TrancheConfiguration

  fun isBookingApplicableForTrancheCriteria(
    calculationResult: CalculationResult,
    bookingSentences: List<CalculableSentence>,
  ): Boolean

  fun filterOnType(
    sentenceToCheck: CalculableSentence,
  ): Boolean {
    // Filter any types excluded by the SI
    // DTO, BOTUS, Fines (terms of imprisonment)
    if (!sentenceToCheck.isDto() && !sentenceToCheck.isBotus() && sentenceToCheck !is AFineSentence) {
      return sentenceToCheck.sentencedAt.isBefore(trancheConfiguration.trancheOneCommencementDate) && sentenceToCheck.sentenceCalculation.adjustedExpiryDate.isAfterOrEqualTo(
        trancheConfiguration.trancheOneCommencementDate,
      )
    }
    return false
  }

  fun filterAndMapSentencesForNotIncludedTypesByDuration(
    sentenceToFilter: CalculableSentence,
    tranche: Tranche,
  ): Long {
    // If the consec sentence's sentencedAt (min of all sentenced at in the chain) was after T1 commencement there is no point in
    // continuing to check its constituent sentences
    return if (sentenceToFilter is ConsecutiveSentence && sentenceToFilter.sentencedAt.isBefore(trancheConfiguration.trancheOneCommencementDate)) {
      val filteredSentences = sentenceToFilter.orderedSentences
        .filter { filterOnType(it) }

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
      if (filterOnType(sentenceToFilter)) {
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
