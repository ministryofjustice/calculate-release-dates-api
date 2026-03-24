package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo

sealed class FTRLegislation(override val configuration: EarlyReleaseConfiguration) : TranchedLegislation {
  override val trancheSelectionStrategy: TrancheSelectionStrategy = FTR56TrancheSelectionStrategy
  open fun isFTR56Supported(): Boolean = false

  abstract fun requiredTimelineCalculations(): List<TimelineCalculationDate>

  data class FTR56Legislation(override val configuration: EarlyReleaseConfiguration) : FTRLegislation(configuration) {
    override fun isFTR56Supported(): Boolean = true

    override fun requiredTimelineCalculations() = configuration.tranches.map { TimelineCalculationDate(it.date, TimelineCalculationType.FTR56_TRANCHE) }
  }

  object FTR56TrancheSelectionStrategy : TrancheSelectionStrategy {
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
            isInRangeOfEarlyRelease && sentencePart.recallType == RecallType.FIXED_TERM_RECALL_56
          }
        }
    }

    /**
     * If sentence is FTR56 recall calculation type, check for exclusions:
     * - revocation date on the same date as earliest tranche (FTR56 commencement)
     */
    private fun isFtr56ExcludedForTrancheRules(sentence: CalculableSentence, earlyReleaseConfig: EarlyReleaseConfiguration): Boolean {
      val isRevocationOnEarliestTranche = sentence.recall?.revocationDate == earlyReleaseConfig.earliestTranche()
      return isRevocationOnEarliestTranche
    }
  }
}
