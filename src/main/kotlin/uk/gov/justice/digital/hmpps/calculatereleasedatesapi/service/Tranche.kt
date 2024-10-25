package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.temporal.ChronoUnit

@Component
class Tranche(
  private val trancheConfiguration: SDS40TrancheConfiguration,
) {

  fun isBookingApplicableForTrancheCriteria(
    calculationResult: CalculationResult,
    bookingSentences: List<CalculableSentence>,
    trancheType: TrancheType,
  ): Boolean {
    val sentenceDurations = bookingSentences.map { filterAndMapSentencesForNotIncludedTypesByDuration(it) }

    return when (trancheType) {
      TrancheType.TRANCHE_ONE -> sentenceDurations.none { it >= 5 }
      TrancheType.TRANCHE_TWO -> sentenceDurations.any { it >= 5 }
    }
  }

  private fun filterAndMapSentencesForNotIncludedTypesByDuration(
    sentenceToFilter: CalculableSentence,
  ): Long {
    return if (sentenceToFilter is ConsecutiveSentence && filterOnSentenceExpiryDates(sentenceToFilter)) {
      val filteredSentences = sentenceToFilter.orderedSentences
        .filter { filterOnType(it) && filterOnSentenceDate(it) }

      if (filteredSentences.isNotEmpty()) {
        val earliestSentencedAt = filteredSentences.minByOrNull { it.sentencedAt } ?: return 0
        var concurrentSentenceEndDate = earliestSentencedAt.sentencedAt

        filteredSentences.forEach { sentence ->
          concurrentSentenceEndDate = sentence.totalDuration()
            .getEndDate(concurrentSentenceEndDate).plusDays(1)
        }

        ChronoUnit.YEARS.between(earliestSentencedAt.sentencedAt, concurrentSentenceEndDate)
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

  private fun filterOnType(sentence: CalculableSentence): Boolean {
    return !sentence.isDto() && !sentence.isBotus() && sentence !is AFineSentence
  }

  private fun filterOnSentenceDate(sentence: CalculableSentence): Boolean {
    return sentence.sentencedAt.isBefore(trancheConfiguration.trancheOneCommencementDate)
  }

  private fun filterOnSentenceExpiryDates(sentence: CalculableSentence): Boolean {
    return sentence.sentenceCalculation.adjustedExpiryDate.isAfterOrEqualTo(trancheConfiguration.trancheOneCommencementDate)
  }
}

enum class TrancheType {
  TRANCHE_ONE,
  TRANCHE_TWO,
}
