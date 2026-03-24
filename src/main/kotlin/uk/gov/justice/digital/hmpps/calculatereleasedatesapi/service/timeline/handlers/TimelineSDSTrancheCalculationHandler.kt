package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationTrigger
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.TrancheAllocationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class TimelineSDSTrancheCalculationHandler(
  timelineCalculator: TimelineCalculator,
  val trancheAllocationService: TrancheAllocationService,
) : AbstractTimelineTrancheHandler(timelineCalculator) {

  override fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      val legislationToApply = requireNotNull(currentTimelineCalculationDate.tranchedLegislationToApplyOnDate) { "Received a tranche allocation timeline event without an allocation piece of legislation on $timelineCalculationDate" }

      val earlyReleaseConfiguration = legislationToApply.configuration
      val tranche = requireNotNull(earlyReleaseConfiguration.tranches.find { it.date == timelineCalculationDate }) { "Couldn't find tranche in early release configuration on $timelineCalculationDate" }

      if (isPersonConsideredOutOfCustodyAtTrancheCommencement(timelineCalculationDate, legislationToApply.commencementDate(), timelineTrackingData)) {
        // The person is considered out of custody and is excluded from early release.
        return TimelineHandleResult(false)
      }

      val requiresTrancheAllocation = earlyReleaseConfiguration.earliestTranche() == tranche.date || allocatedTranche == null
      if (requiresTrancheAllocation) {
        val allocated = trancheAllocationService.allocateTranche(timelineTrackingData, legislationToApply)
        if (allocated != null && allocated.date.isAfterOrEqualTo(timelineCalculationDate)) {
          allocatedTranche = allocated
          allocatedEarlyRelease = earlyReleaseConfiguration
          if (allocated.name != null) trancheAllocationByCategory[allocated.name.category] = allocated.name
        }
      }

      val thisTrancheIsAllocatedTranche = allocatedTranche?.date == tranche.date
      val sentencesToModifyReleaseDates = sentencesToModifyReleaseDates(timelineTrackingData, timelineCalculationDate, earlyReleaseConfiguration)
      if (thisTrancheIsAllocatedTranche && sentencesToModifyReleaseDates.isNotEmpty()) {
        val allSentences = releasedSentenceGroups.map { it.sentences }.plus(listOf(currentSentenceGroup))
        beforeTrancheCalculation = if (earlyReleaseConfiguration.additionsAppliedAfterDefaulting) {
          null
        } else {
          timelineCalculator.getLatestCalculation(allSentences, offender, timelineTrackingData.returnToCustodyDate)
        }
        sentencesToModifyReleaseDates.forEach {
          it.sentenceCalculation.allocatedTranche = tranche
          it.sentenceCalculation.allocatedEarlyRelease = earlyReleaseConfiguration
          it.sentenceCalculation.unadjustedReleaseDate.calculationTrigger = CalculationTrigger(
            timelineCalculationDate,
            allocatedEarlyRelease,
            allocatedTranche,
          )
          it.sentenceCalculation.adjustments = it.sentenceCalculation.adjustments.copy(
            unusedAdaDays = 0,
            unusedLicenceAdaDays = 0,
          )
        }
      } else {
        // No sentences at tranche date.
        return TimelineHandleResult(requiresCalculation = false)
      }
    }
    return TimelineHandleResult()
  }

  fun sentencesToModifyReleaseDates(
    timelineTrackingData: TimelineTrackingData,
    timelineCalculationDate: LocalDate,
    earlyReleaseConfiguration: EarlyReleaseConfiguration,
  ): List<CalculableSentence> = (timelineTrackingData.currentSentenceGroup + timelineTrackingData.licenceSentences).filter {
    it.sentenceCalculation.adjustedDeterminateReleaseDate.isAfter(timelineCalculationDate)
  }
    .filter { sentence -> sentence.sentenceParts().any { earlyReleaseConfiguration.matchesFilter(it) } }
}
