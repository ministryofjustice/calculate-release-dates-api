package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.temporal.ChronoUnit
import kotlin.collections.map

@Service
class TrancheAllocationService {

  fun allocateTranche(timelineTrackingData: TimelineTrackingData, earlyReleaseConfig: EarlyReleaseConfiguration): EarlyReleaseTrancheConfiguration? {
    val sentencesWithReleaseAfterTrancheCommencement = earlyReleaseConfig.sentencesWithReleaseAfterTrancheCommencement(timelineTrackingData.currentSentenceGroup + timelineTrackingData.licenceSentences)
    val sentencesConsideredForTrancheRules = getSentencesForTrancheRules(sentencesWithReleaseAfterTrancheCommencement, earlyReleaseConfig)
    if (sentencesConsideredForTrancheRules.isEmpty()) {
      return null
    }

    if (timelineTrackingData.allocatedTranche != null && earlyReleaseConfig.tranches.any { it == timelineTrackingData.allocatedTranche }) {
      // Already allocated to a tranche in this early release group.
      return null
    }

    val allSentences = timelineTrackingData.licenceSentences + timelineTrackingData.currentSentenceGroup
    return findTranche(earlyReleaseConfig, allSentences)
  }

  private fun findTranche(
    earlyReleaseConfig: EarlyReleaseConfiguration,
    allSentences: List<CalculableSentence>,
  ): EarlyReleaseTrancheConfiguration? = earlyReleaseConfig.tranches.find {
    when (it.type) {
      EarlyReleaseTrancheType.SENTENCE_LENGTH -> matchesSentenceLength(allSentences, earlyReleaseConfig, it)
      EarlyReleaseTrancheType.FINAL -> true // Not matched any other tranche, so must be in this one.
      EarlyReleaseTrancheType.SDS_40_TRANCHE_3 -> false // Not really a tranche that can be allocated to.
    }
  }

  private fun matchesSentenceLength(
    sentencesConsideredForTrancheRules: List<CalculableSentence>,
    earlyReleaseConfiguration: EarlyReleaseConfiguration,
    tranche: EarlyReleaseTrancheConfiguration,
  ): Boolean {
    val sentenceDurations = sentencesConsideredForTrancheRules.map { filterAndMapSentencesForNotIncludedTypesByDuration(it, earlyReleaseConfiguration, tranche.unit!!) }
    return sentenceDurations.none { it >= tranche.duration!! }
  }

  private fun getSentencesForTrancheRules(
    sentences: List<CalculableSentence>,
    earlyReleaseConfig: EarlyReleaseConfiguration,
  ): List<CalculableSentence> = sentences.filter {
    it.sentenceParts().any { sentence ->
      isEligibleForTrancheRules(earlyReleaseConfig, sentence) &&
        sentence.sentencedAt.isBefore(earlyReleaseConfig.earliestTranche())
    }
  }

  private fun isEligibleForTrancheRules(earlyReleaseConfiguration: EarlyReleaseConfiguration, sentence: CalculableSentence): Boolean = if (earlyReleaseConfiguration.releaseMultiplier != null) {
    earlyReleaseConfiguration.releaseMultiplier.keys.contains(sentence.identificationTrack)
  } else {
    sentence.sentenceParts().any { earlyReleaseConfiguration.matchesFilter(it) }
  }

  private fun filterAndMapSentencesForNotIncludedTypesByDuration(
    sentenceToFilter: CalculableSentence,
    earlyReleaseConfiguration: EarlyReleaseConfiguration,
    unit: ChronoUnit,
  ): Long {
    return if (sentenceToFilter is ConsecutiveSentence && filterOnSentenceExpiryDates(sentenceToFilter, earlyReleaseConfiguration)) {
      val filteredSentences = sentenceToFilter.orderedSentences
        .filter { filterOnType(it) && filterOnSentenceDate(it, earlyReleaseConfiguration) }

      if (filteredSentences.isNotEmpty()) {
        val earliestSentencedAt = filteredSentences.minByOrNull { it.sentencedAt } ?: return 0
        var concurrentSentenceEndDate = earliestSentencedAt.sentencedAt

        filteredSentences.forEach { sentence ->
          concurrentSentenceEndDate = sentence.totalDuration()
            .getEndDate(concurrentSentenceEndDate).plusDays(1)
        }

        unit.between(earliestSentencedAt.sentencedAt, concurrentSentenceEndDate)
      } else {
        0
      }
    } else {
      if (filterOnType(sentenceToFilter) && filterOnSentenceDate(sentenceToFilter, earlyReleaseConfiguration) && filterOnSentenceExpiryDates(sentenceToFilter, earlyReleaseConfiguration)) {
        unit.between(
          sentenceToFilter.sentencedAt,
          sentenceToFilter.sentencedAt.plus(sentenceToFilter.getLengthInDays().toLong(), ChronoUnit.DAYS).plusDays(1),
        )
      } else {
        0
      }
    }
  }

  private fun filterOnType(sentence: CalculableSentence): Boolean = !sentence.isDto() && !sentence.isOrExclusivelyBotus() && sentence !is AFineSentence

  private fun filterOnSentenceDate(sentence: CalculableSentence, earlyReleaseConfiguration: EarlyReleaseConfiguration): Boolean = sentence.sentencedAt.isBefore(earlyReleaseConfiguration.earliestTranche())

  private fun filterOnSentenceExpiryDates(sentence: CalculableSentence, earlyReleaseConfiguration: EarlyReleaseConfiguration): Boolean = sentence.sentenceCalculation.adjustedExpiryDate.isAfterOrEqualTo(earlyReleaseConfiguration.earliestTranche())
}
