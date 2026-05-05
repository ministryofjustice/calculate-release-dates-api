package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

abstract class SDSTrancheSelectionStrategy : TrancheSelectionStrategy {

  class SDS40TrancheSelectionStrategy : SDSTrancheSelectionStrategy() {
    override fun hasSentencesThatMightApplyToTheTranche(
      timelineTrackingData: TimelineTrackingData,
      legislation: Legislation,
    ): Boolean {
      require(legislation is SDSLegislation.SDS40Legislation) { "Tried to allocate incorrect legislation using SDS40TrancheSelectionStrategy" }
      val sentencesWithReleaseAfterTrancheCommencement = (timelineTrackingData.currentSentenceGroup + timelineTrackingData.licenceSentences).filter {
        it.sentenceCalculation.adjustedDeterminateReleaseDate.isAfter(legislation.commencementDate())
      }
      return sentencesWithReleaseAfterTrancheCommencement.any { sentence ->
        sentence.sentenceParts().any { sentencePart ->
          sentencePart.isInRangeOfEarlyRelease(legislation.commencementDate()) && legislation.isSentenceSubjectToTraches(sentencePart)
        }
      }
    }
  }

  class SDSProgressionModelTrancheSelectionStrategy : SDSTrancheSelectionStrategy() {
    override fun hasSentencesThatMightApplyToTheTranche(
      timelineTrackingData: TimelineTrackingData,
      legislation: Legislation,
    ): Boolean {
      require(legislation is SDSLegislation.ProgressionModelLegislation) { "Tried to allocate incorrect legislation using SDSProgressionModelTrancheSelectionStrategy" }
      val sentencesWithReleaseAfterTrancheCommencement = (timelineTrackingData.currentSentenceGroup + timelineTrackingData.licenceSentences).filter {
        // UAL awarded during custody for this sentence will have already been applied but if the UAL is on or after commencement then it shouldn't be
        // considered for tranche eligibility so subtract it from the calculated CRD
        val ualThatHasNotOccurredYet = timelineTrackingData.previousUalPeriods
          .filter { ual -> ual.first.isAfterOrEqualTo(legislation.commencementDate()) }
          .sumOf { ual -> ChronoUnit.DAYS.between(ual.first, ual.second) }
        val adjustedDeterminateReleaseDateExcludingFutureUAL = it.sentenceCalculation.adjustedDeterminateReleaseDate.minusDays(ualThatHasNotOccurredYet)
        adjustedDeterminateReleaseDateExcludingFutureUAL.isAfterOrEqualTo(legislation.commencementDate())
      }
      return sentencesWithReleaseAfterTrancheCommencement.any { sentence ->
        sentence.sentenceParts().any { sentencePart ->
          sentencePart.isInRangeOfEarlyRelease(legislation.commencementDate()) && legislation.isSentenceSubjectToTraches(sentencePart)
        }
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
  ) = trancheConfig.duration is Int && durations.none { it >= trancheConfig.duration }

  companion object {
    private fun AbstractSentence.isInRangeOfEarlyRelease(commencementDate: LocalDate): Boolean = sentencedAt.isBefore(commencementDate)
  }
}
