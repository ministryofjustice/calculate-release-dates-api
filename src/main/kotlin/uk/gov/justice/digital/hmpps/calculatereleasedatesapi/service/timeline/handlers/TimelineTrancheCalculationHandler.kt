package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfigurations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
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

      val earlyReleaseConfiguration = currentTimelineCalculationDate.earlyReleaseConfiguration!!
      val tranche = currentTimelineCalculationDate.trancheConfiguration!!

      val allSentences = releasedSentenceGroups.map { it.sentences }.plus(listOf(currentSentenceGroup))
      val potentialEarlyReleaseSentences = getPotentialEarlyReleaseSentences(allSentences.flatten(), earlyReleaseConfiguration)
      if (potentialEarlyReleaseSentences.isNotEmpty() && potentialEarlyReleaseSentences.none { it.sentenceCalculation.adjustedDeterminateReleaseDate.isAfter(earlyReleaseConfiguration.earliestTranche()) }) {
        return TimelineHandleResult(false)
      }

      val requiresTrancheAllocation = earlyReleaseConfiguration.earliestTranche() == tranche.date || allocatedTranche == null
      if (requiresTrancheAllocation) {
        val allocated = trancheAllocationService.allocateTranche(timelineTrackingData, earlyReleaseConfiguration)
        if (allocated != null) {
          allocatedTranche = allocated
          allocatedEarlyRelease = earlyReleaseConfiguration
        }
      }

      val thisTrancheIsAllocatedTranche = allocatedTranche?.date == tranche.date
      if (thisTrancheIsAllocatedTranche && potentialEarlyReleaseSentences.isNotEmpty()) {
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
  private fun getPotentialEarlyReleaseSentences(allSentences: List<CalculableSentence>, earlyReleaseConfiguration: EarlyReleaseConfiguration): List<CalculableSentence> = allSentences.filter { sentence -> sentence.sentenceParts().any { earlyReleaseConfiguration.matchesFilter(it) } }
}
