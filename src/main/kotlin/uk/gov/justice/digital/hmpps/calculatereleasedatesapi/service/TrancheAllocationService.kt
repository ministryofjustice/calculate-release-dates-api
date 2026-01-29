package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseSentenceFilter
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
import java.time.temporal.ChronoUnit

@Service
class TrancheAllocationService {

  fun allocateTranche(timelineTrackingData: TimelineTrackingData, earlyReleaseConfig: EarlyReleaseConfiguration): EarlyReleaseTrancheConfiguration? {
    val trancheSelectionStrategy = if (earlyReleaseConfig.filter == EarlyReleaseSentenceFilter.FTR_56) {
      FTR56TrancheSelectionStrategy
    } else {
      SDSTrancheSelectionStrategy
    }
    if (!trancheSelectionStrategy.hasSentencesThatMightApplyToTheTranche(timelineTrackingData, earlyReleaseConfig)) {
      return null
    }

    if (timelineTrackingData.allocatedTranche != null && earlyReleaseConfig.tranches.any { it == timelineTrackingData.allocatedTranche }) {
      // Already allocated to a tranche in this early release group.
      return null
    }

    return findTranche(earlyReleaseConfig, trancheSelectionStrategy.sentencesToMatchOnSentenceLength(timelineTrackingData, earlyReleaseConfig))
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

  private fun filterAndMapSentencesForNotIncludedTypesByDuration(
    sentenceToFilter: CalculableSentence,
    earlyReleaseConfiguration: EarlyReleaseConfiguration,
    unit: ChronoUnit,
  ): Long {
    return if (sentenceToFilter is ConsecutiveSentence) {
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
      if (filterOnType(sentenceToFilter) && filterOnSentenceDate(sentenceToFilter, earlyReleaseConfiguration)) {
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

  private sealed interface TrancheSelectionStrategy {
    fun hasSentencesThatMightApplyToTheTranche(timelineTrackingData: TimelineTrackingData, earlyReleaseConfig: EarlyReleaseConfiguration): Boolean
    fun sentencesToMatchOnSentenceLength(timelineTrackingData: TimelineTrackingData, earlyReleaseConfig: EarlyReleaseConfiguration): List<CalculableSentence>
  }

  private object SDSTrancheSelectionStrategy : TrancheSelectionStrategy {
    override fun hasSentencesThatMightApplyToTheTranche(
      timelineTrackingData: TimelineTrackingData,
      earlyReleaseConfig: EarlyReleaseConfiguration,
    ): Boolean {
      val sentencesWithReleaseAfterTrancheCommencement = (timelineTrackingData.currentSentenceGroup + timelineTrackingData.licenceSentences).filter {
        earlyReleaseConfig.releaseDateConsidered(it.sentenceCalculation).isAfter(earlyReleaseConfig.earliestTranche())
      }
      return sentencesWithReleaseAfterTrancheCommencement.filter { sentence ->
        sentence.sentenceParts().any { sentencePart ->
          val isInRangeOfEarlyRelease = sentencePart.sentencedAt.isBefore(earlyReleaseConfig.earliestTranche())
          isInRangeOfEarlyRelease && earlyReleaseConfig.isEligibleForTrancheRules(sentencePart)
        }
      }.isNotEmpty()
    }

    override fun sentencesToMatchOnSentenceLength(
      timelineTrackingData: TimelineTrackingData,
      earlyReleaseConfig: EarlyReleaseConfiguration,
    ): List<CalculableSentence> = (timelineTrackingData.licenceSentences + timelineTrackingData.currentSentenceGroup)
      .filter { filterOnSentenceExpiryDates(it, earlyReleaseConfig) }

    private fun filterOnSentenceExpiryDates(sentence: CalculableSentence, earlyReleaseConfiguration: EarlyReleaseConfiguration): Boolean = sentence.sentenceCalculation.adjustedExpiryDate.isAfterOrEqualTo(earlyReleaseConfiguration.earliestTranche())
  }

  private object FTR56TrancheSelectionStrategy : TrancheSelectionStrategy {
    override fun hasSentencesThatMightApplyToTheTranche(
      timelineTrackingData: TimelineTrackingData,
      earlyReleaseConfig: EarlyReleaseConfiguration,
    ): Boolean = sentencesConsideredForFTR56TrancheCommencement(timelineTrackingData, earlyReleaseConfig).isNotEmpty()

    override fun sentencesToMatchOnSentenceLength(
      timelineTrackingData: TimelineTrackingData,
      earlyReleaseConfig: EarlyReleaseConfiguration,
    ) = sentencesConsideredForFTR56TrancheCommencement(timelineTrackingData, earlyReleaseConfig)

    private fun sentencesConsideredForFTR56TrancheCommencement(timelineTrackingData: TimelineTrackingData, earlyReleaseConfig: EarlyReleaseConfiguration): List<CalculableSentence> {
      val allFtr56Sentences = (timelineTrackingData.currentSentenceGroup + timelineTrackingData.licenceSentences + timelineTrackingData.expiredLicenceSentences)
        .filter { it.recallType == RecallType.FIXED_TERM_RECALL_56 }
      val maxRevocationDate = allFtr56Sentences
        .mapNotNull { it.recall?.revocationDate }.maxOrNull()
      if (maxRevocationDate == null) {
        // there were no recalls
        return emptyList()
      }
      return allFtr56Sentences
        .filter { ftr56Sentence -> ftr56Sentence.sentenceCalculation.adjustedExpiryDate.isAfter(maxRevocationDate) }
        .filter { sentenceExpiringAfterLatestRevocationDate -> !isFtr56ExcludedForTrancheRules(sentenceExpiringAfterLatestRevocationDate, earlyReleaseConfig) }
        .filter { sentenceNotExcludedFromTranchingRules ->
          sentenceNotExcludedFromTranchingRules.sentenceParts().any { sentencePart ->
            val isInRangeOfEarlyRelease = sentencePart.recall?.returnToCustodyDate?.isBeforeOrEqualTo(earlyReleaseConfig.earliestTranche()) == true
            isInRangeOfEarlyRelease && earlyReleaseConfig.isEligibleForTrancheRules(sentencePart)
          }
        }
    }

    /**
     * If sentence is FTR56 recall calculation type, check for exclusions:
     * - sentence duration under 4 years
     * - revocation date on the same date as earliest tranche (FTR56 commencement)
     */
    private fun isFtr56ExcludedForTrancheRules(sentence: CalculableSentence, earlyReleaseConfig: EarlyReleaseConfiguration): Boolean {
      val isRevocationOnEarliestTranche = sentence.recall?.revocationDate == earlyReleaseConfig.earliestTranche()
      return isRevocationOnEarliestTranche
    }
  }
}
