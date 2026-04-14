package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.FTR56TrancheTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
import java.time.LocalDate

sealed interface FTRLegislation : Legislation {
  fun isFTR56Supported(): Boolean = false

  fun requiredTimelineCalculations(): List<TimelineCalculationEvent>

  data class FTR56Legislation(override val tranches: List<TrancheConfiguration>) :
    FTRLegislation,
    LegislationWithTranches {
    override val legislationName = LegislationName.FTR_56
    override val trancheSelectionStrategy: TrancheSelectionStrategy = FTR56TrancheSelectionStrategy

    override fun isFTR56Supported(): Boolean = true

    override fun requiredTimelineCalculations() = tranches.map { tranche -> FTR56TrancheTimelineCalculationEvent(tranche.date, legislation = this, tranche) }

    override fun commencementDate(): LocalDate = tranches.minOf { it.date }
  }

  object FTR56TrancheSelectionStrategy : TrancheSelectionStrategy {
    override fun hasSentencesThatMightApplyToTheTranche(
      timelineTrackingData: TimelineTrackingData,
      legislation: Legislation,
    ): Boolean = sentencesConsideredForFTR56TrancheCommencement(timelineTrackingData, legislation).isNotEmpty()

    override fun sentencesToMatchOnSentenceLength(
      timelineTrackingData: TimelineTrackingData,
      legislation: Legislation,
    ) = sentencesConsideredForFTR56TrancheCommencement(timelineTrackingData, legislation)

    private fun sentencesConsideredForFTR56TrancheCommencement(timelineTrackingData: TimelineTrackingData, legislation: Legislation): List<CalculableSentence> {
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
        .filter { sentenceExpiringAfterLatestRevocationDate -> !isFtr56ExcludedForTrancheRules(sentenceExpiringAfterLatestRevocationDate, legislation.commencementDate()) }
        .filter { sentenceNotExcludedFromTranchingRules ->
          sentenceNotExcludedFromTranchingRules.sentenceParts().any { sentencePart ->
            val isInRangeOfEarlyRelease = sentencePart.recall?.returnToCustodyDate?.isBeforeOrEqualTo(legislation.commencementDate()) == true
            isInRangeOfEarlyRelease && sentencePart.recallType == RecallType.FIXED_TERM_RECALL_56
          }
        }
    }

    /**
     * If sentence is FTR56 recall calculation type, check for exclusions:
     * - revocation date on the same date as earliest tranche (FTR56 commencement)
     */
    private fun isFtr56ExcludedForTrancheRules(sentence: CalculableSentence, legislationCommencementDate: LocalDate): Boolean {
      val isRevocationOnEarliestTranche = sentence.recall?.revocationDate == legislationCommencementDate
      return isRevocationOnEarliestTranche
    }
  }
}
