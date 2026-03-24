package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo

sealed class SDSLegislation(override val configuration: EarlyReleaseConfiguration) : TranchedLegislation {
  override val trancheSelectionStrategy: TrancheSelectionStrategy = SDSTrancheSelectionStrategy
  abstract fun requiredTimelineCalculations(): List<TimelineCalculationDate>

  data class SDS40Legislation(override val configuration: EarlyReleaseConfiguration) : SDSLegislation(configuration) {
    override fun requiredTimelineCalculations(): List<TimelineCalculationDate> = configuration.tranches.map {
      if (it.type == EarlyReleaseTrancheType.SDS_40_TRANCHE_3) {
        TimelineCalculationDate(it.date, TimelineCalculationType.SDS_40_TRANCHE_3)
      } else {
        TimelineCalculationDate(it.date, TimelineCalculationType.EARLY_RELEASE_TRANCHE, this)
      }
    }
  }

  data class ProgressionModelLegislation(override val configuration: EarlyReleaseConfiguration) : SDSLegislation(configuration) {
    override fun requiredTimelineCalculations(): List<TimelineCalculationDate> = configuration.tranches.map {
      TimelineCalculationDate(it.date, TimelineCalculationType.EARLY_RELEASE_TRANCHE, this)
    }
  }

  private object SDSTrancheSelectionStrategy : TrancheSelectionStrategy {
    override fun hasSentencesThatMightApplyToTheTranche(
      timelineTrackingData: TimelineTrackingData,
      earlyReleaseConfig: EarlyReleaseConfiguration,
    ): Boolean {
      val sentencesWithReleaseAfterTrancheCommencement = (timelineTrackingData.currentSentenceGroup + timelineTrackingData.licenceSentences).filter {
        it.sentenceCalculation.adjustedDeterminateReleaseDate.isAfter(earlyReleaseConfig.earliestTranche())
      }
      return sentencesWithReleaseAfterTrancheCommencement.any { sentence ->
        sentence.sentenceParts().any { sentencePart ->
          val isInRangeOfEarlyRelease = sentencePart.sentencedAt.isBefore(earlyReleaseConfig.earliestTranche())
          isInRangeOfEarlyRelease && earlyReleaseConfig.isEligibleForTrancheRules(sentencePart)
        }
      }
    }

    override fun sentencesToMatchOnSentenceLength(
      timelineTrackingData: TimelineTrackingData,
      earlyReleaseConfig: EarlyReleaseConfiguration,
    ): List<CalculableSentence> = (timelineTrackingData.licenceSentences + timelineTrackingData.currentSentenceGroup)
      .filter { filterOnSentenceExpiryDates(it, earlyReleaseConfig) }

    private fun filterOnSentenceExpiryDates(sentence: CalculableSentence, earlyReleaseConfiguration: EarlyReleaseConfiguration): Boolean = sentence.sentenceCalculation.adjustedExpiryDate.isAfterOrEqualTo(earlyReleaseConfiguration.earliestTranche())
  }
}
