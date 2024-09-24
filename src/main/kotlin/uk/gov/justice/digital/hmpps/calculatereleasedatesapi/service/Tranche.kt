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

  fun filterAndMapSentencesForNotIncludedTypesByDuration(
    sentenceToFilter: CalculableSentence,
    tranche: Tranche,
  ): Long {
    return if (sentenceToFilter is ConsecutiveSentence && filterOnSentenceExpiryDates(sentenceToFilter)) {
      val filteredSentences = sentenceToFilter.orderedSentences
        .filter { filterOnType(it) && filterOnSentenceDate(it) }

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
      if (filterOnType(sentenceToFilter) && filterOnSentenceDate(sentenceToFilter) && filterOnSentenceExpiryDates(sentenceToFilter)) {
        ChronoUnit.YEARS.between(
          sentenceToFilter.sentencedAt,
          sentenceToFilter.sentencedAt.plus(sentenceToFilter.getLengthInDays().toLong(), ChronoUnit.DAYS).plusDays(1),
        )
      } else {
        0
      }
    }
  }

  private fun filterOnType(
    sentence: CalculableSentence,
  ): Boolean {
    // Filter any types excluded by the SI
    // DTO, BOTUS, Fines (terms of imprisonment)
    return !sentence.isDto() && !sentence.isBotus() && sentence !is AFineSentence
  }

  private fun filterOnSentenceDate(sentence: CalculableSentence): Boolean {
    return sentence.sentencedAt.isBefore(trancheConfiguration.trancheOneCommencementDate)
  }

  private fun filterOnSentenceExpiryDates(
    sentence: CalculableSentence,
  ): Boolean {
    return sentence.sentenceCalculation.adjustedExpiryDate.isAfterOrEqualTo(
      trancheConfiguration.trancheOneCommencementDate,
    )
  }
}
