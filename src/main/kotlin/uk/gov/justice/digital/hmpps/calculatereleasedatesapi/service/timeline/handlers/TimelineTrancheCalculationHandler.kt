package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfigurations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.TrancheAllocationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import java.time.LocalDate

@Service
class TimelineTrancheCalculationHandler(
  timelineCalculator: TimelineCalculator,
  earlyReleaseConfigurations: EarlyReleaseConfigurations,
  val trancheAllocationService: TrancheAllocationService,
) : TimelineCalculationHandler(timelineCalculator, earlyReleaseConfigurations) {

  override fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      val currentNonRecallSentences = currentSentenceGroup.filter { !it.isRecall() }
      if (currentNonRecallSentences.isEmpty() || !inPrison) {
        return TimelineHandleResult(false)
      }
      val isIncludedInTranche = trancheAllocationService.isIncludedInTranche(timelineTrackingData)

      if (isIncludedInTranche) {
        allocatedTranche = currentTimelineCalculationDate.trancheConfiguration
        allocatedEarlyRelease = currentTimelineCalculationDate.earlyReleaseConfiguration
        val allSentences = releasedSentenceGroups.map { it.sentences }.plus(listOf(currentSentenceGroup))
        beforeTrancheCalculation =
          timelineCalculator.getLatestCalculation(allSentences, offender, timelineTrackingData.returnToCustodyDate)
        currentSentenceGroup.forEach {
          it.sentenceCalculation.unadjustedReleaseDate.findMultiplierBySentence =
            multiplierFnForDate(timelineCalculationDate, allocatedTranche!!.date)
          it.sentenceCalculation.adjustments = it.sentenceCalculation.adjustments.copy(
            unusedAdaDays = 0,
            unusedLicenceAdaDays = 0,
          )
          it.sentenceCalculation.allocatedTranche = currentTimelineCalculationDate.trancheConfiguration
          it.sentenceCalculation.allocatedEarlyRelease = currentTimelineCalculationDate.earlyReleaseConfiguration
        }
      } else {
        // No sentences at tranche date.
        return TimelineHandleResult(requiresCalculation = false)
      }
    }
    return TimelineHandleResult()
  }
}
