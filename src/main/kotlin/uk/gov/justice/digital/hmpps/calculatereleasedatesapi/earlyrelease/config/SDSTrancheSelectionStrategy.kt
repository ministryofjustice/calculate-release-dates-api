package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo

object SDSTrancheSelectionStrategy : TrancheSelectionStrategy {
  override fun hasSentencesThatMightApplyToTheTranche(
    timelineTrackingData: TimelineTrackingData,
    legislation: Legislation,
  ): Boolean {
    require(legislation is SDSLegislationWithTranches) { "Tried to allocate non SDSLegislation using SDS tranche allocation strategy" }
    val sentencesWithReleaseAfterTrancheCommencement = (timelineTrackingData.currentSentenceGroup + timelineTrackingData.licenceSentences).filter {
      it.sentenceCalculation.adjustedDeterminateReleaseDate.isAfter(legislation.commencementDate())
    }
    return sentencesWithReleaseAfterTrancheCommencement.any { sentence ->
      sentence.sentenceParts().any { sentencePart ->
        val isInRangeOfEarlyRelease = sentencePart.sentencedAt.isBefore(legislation.commencementDate())
        isInRangeOfEarlyRelease && legislation.isSentenceSubjectToTraches(sentencePart)
      }
    }
  }

  override fun sentencesToMatchOnSentenceLength(
    timelineTrackingData: TimelineTrackingData,
    legislation: Legislation,
  ): List<CalculableSentence> = (timelineTrackingData.licenceSentences + timelineTrackingData.currentSentenceGroup)
    .filter { filterOnSentenceExpiryDates(it, legislation) }

  private fun filterOnSentenceExpiryDates(sentence: CalculableSentence, legislation: Legislation): Boolean = sentence.sentenceCalculation.adjustedExpiryDate.isAfterOrEqualTo(legislation.commencementDate())

  override fun sentenceDurationsWithinTrancheDuration(
    trancheConfig: TrancheConfiguration,
    durations: List<Long>,
  ) = trancheConfig.duration?.let { duration -> durations.none { it >= duration } } ?: true
}
