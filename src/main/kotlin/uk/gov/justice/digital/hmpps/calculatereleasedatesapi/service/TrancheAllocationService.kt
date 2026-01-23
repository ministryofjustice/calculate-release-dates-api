package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.RecallCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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

  /**
   * Sentences under 4 years that were recalled before FTR_56 commencement should be treated as FTR_56 sentences
   * which are not subject to tranche.
   *
   * FTR_56 revocation date must be on or after the start of FTR_56 tranche one to be valid for tranche rules.
   */
  private fun getSentencesForTrancheRules(
    sentences: List<CalculableSentence>,
    earlyReleaseConfig: EarlyReleaseConfiguration,
  ): List<CalculableSentence> {
    val maxFtr56RevocationDate = sentences
      .filter { it.recallType == RecallType.FIXED_TERM_RECALL_56 }
      .maxBy { it.recall?.revocationDate ?: LocalDate.MIN }.recall?.revocationDate ?: LocalDate.MIN

    return sentences.filter { sentence ->
      val excludedFromFtr56Tranche = isFtr56ExcludedForTrancheRules(sentence, maxFtr56RevocationDate, earlyReleaseConfig)
      sentence.sentenceParts().any { sentencePart ->
        val isInRangeOfEarlyRelease = if (earlyReleaseConfig.modifiesRecallReleaseDate()) {
          !excludedFromFtr56Tranche && sentencePart.recall?.returnToCustodyDate?.isBeforeOrEqualTo(earlyReleaseConfig.earliestTranche()) == true
        } else {
          sentencePart.sentencedAt.isBefore(earlyReleaseConfig.earliestTranche())
        }
        isInRangeOfEarlyRelease && isEligibleForTrancheRules(earlyReleaseConfig, sentencePart)
      }
    }
  }

  /**
   * If sentence is FTR56 recall calculation type, check for exclusions:
   * - sentence duration under 4 years
   * - revocation date on the same date as earliest tranche (FTR56 commencement)
   * - FTR56 sentence has SLED (adjusted expiry date) before latest revocation date
   */
  private fun isFtr56ExcludedForTrancheRules(
    sentence: CalculableSentence,
    latestRevocationDate: LocalDate,
    earlyReleaseConfig: EarlyReleaseConfiguration,
  ): Boolean {
    if (earlyReleaseConfig.recallCalculation !== RecallCalculationType.FTR_56) return false
    val isShortSentence = sentence.durationIsLessThan(1461, ChronoUnit.DAYS)
    val isRevocationOnEarliestTranche = sentence.recall?.revocationDate == earlyReleaseConfig.earliestTranche()
    val ftr56SentenceHasExpired = sentence.sentenceCalculation.adjustedExpiryDate.isBefore(latestRevocationDate)

    return isShortSentence || isRevocationOnEarliestTranche || ftr56SentenceHasExpired
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
