package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLengthComparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLengthRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLengthSentence
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS

@Service
class OverallSentenceLengthService {

  fun compare(overallSentenceLengthRequest: OverallSentenceLengthRequest): OverallSentenceLengthComparison {
    val compareCombined = compareSentenceLength(overallSentenceLengthRequest) { it.combinedDuration() }

    if (overallSentenceLengthRequest.overallSentenceLength.extensionDuration != null) {
      val compareCustodial = compareSentenceLength(overallSentenceLengthRequest) { it.custodialDuration.toDuration() }
      return OverallSentenceLengthComparison(
        compareCustodial.second,
        getLicenseLength(compareCustodial.second, compareCombined.second, overallSentenceLengthRequest.warrantDate),
        compareCustodial.first,
        compareCombined.first,
      )
    } else {
      return OverallSentenceLengthComparison(
        compareCombined.second,
        null,
        compareCombined.first,
        null,
      )
    }
  }

  private fun compareSentenceLength(overallSentenceLengthRequest: OverallSentenceLengthRequest, durationSupplier: (sentence: OverallSentenceLengthSentence) -> Duration): Pair<Boolean, Duration> {
    val sentenceAt = overallSentenceLengthRequest.warrantDate
    val durationsToCompare = overallSentenceLengthRequest.concurrentSentences.map(durationSupplier).toMutableList()
    if (overallSentenceLengthRequest.consecutiveSentences.isNotEmpty()) {
      val consecutiveAggregator = DurationAggregator(
        overallSentenceLengthRequest.consecutiveSentences.map(durationSupplier),
      )
      val consecutiveAggregate = consecutiveAggregator.aggregate()
      val consecutiveDuration = if (consecutiveAggregate.size == 1) {
        consecutiveAggregate[0]
      } else {
        Duration(mapOf(DAYS to consecutiveAggregator.calculateDays(sentenceAt).toLong()))
      }
      durationsToCompare.add(consecutiveDuration)
    }

    val latestEndDuration = durationsToCompare.maxBy {
      it.getEndDate(sentenceAt)
    }
    val latestEnd = latestEndDuration.getEndDate(sentenceAt)
    val overallEnd = durationSupplier(overallSentenceLengthRequest.overallSentenceLength).getEndDate(sentenceAt)
    val matches = latestEnd == overallEnd
    return matches to latestEndDuration
  }

  private fun hasSameUnits(custodial: Duration, combined: Duration): Boolean = custodial.durationElements.filter { (_, days) -> days != 0L }.map { it.key } == combined.durationElements.filter { (_, days) -> days != 0L }.map { it.key }

  private fun getLicenseLength(custodial: Duration, combined: Duration, sentenceAt: LocalDate): Duration {
    if (hasSameUnits(custodial, combined)) {
      return combined.removeAll(custodial.durationElements)
    } else {
      val combinedDays = custodial.getLengthInDays(sentenceAt)
      val custodialDays = custodial.getLengthInDays(sentenceAt)
      return Duration(mapOf(DAYS to (combinedDays - custodialDays).toLong()))
    }
  }
}
