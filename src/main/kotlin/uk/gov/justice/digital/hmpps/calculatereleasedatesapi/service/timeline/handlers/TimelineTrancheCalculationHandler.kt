package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfigurations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.TrancheAllocationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
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
      val earlyReleaseConfiguration = currentTimelineCalculationDate.earlyReleaseConfiguration!!
      val tranche = currentTimelineCalculationDate.trancheConfiguration!!

      if (isPersonConsideredOutOfCustodyAtTrancheCommencement(timelineCalculationDate, earlyReleaseConfiguration, timelineTrackingData)) {
        // The person is considered out of custody and is excluded from early release.
        return TimelineHandleResult(false)
      }

      val allSentences = releasedSentenceGroups.map { it.sentences }.plus(listOf(currentSentenceGroup))
      val potentialEarlyReleaseSentences = getPotentialEarlyReleaseSentences(allSentences.flatten(), earlyReleaseConfiguration, offender)
      if (potentialEarlyReleaseSentences.isNotEmpty() && potentialEarlyReleaseSentences.none { it.sentenceCalculation.adjustedDeterminateReleaseDate.isAfter(earlyReleaseConfiguration.earliestTranche()) }) {
        return TimelineHandleResult(false)
      }

      val requiresTrancheAllocation = earlyReleaseConfiguration.earliestTranche() == tranche.date || allocatedTranche == null
      if (requiresTrancheAllocation) {
        val allocated = trancheAllocationService.allocateTranche(timelineTrackingData, earlyReleaseConfiguration)
        if (allocated != null && allocated.date.isAfterOrEqualTo(timelineCalculationDate)) {
          allocatedTranche = allocated
          allocatedEarlyRelease = earlyReleaseConfiguration
        }
      }

      val thisTrancheIsAllocatedTranche = allocatedTranche?.date == tranche.date
      if (thisTrancheIsAllocatedTranche && potentialEarlyReleaseSentences.isNotEmpty()) {
        beforeTrancheCalculation =
          timelineCalculator.getLatestCalculation(allSentences, offender, timelineTrackingData.returnToCustodyDate)
        sentencesBeforeReleaseDate(timelineCalculationDate).forEach {
          it.sentenceCalculation.unadjustedReleaseDate.findMultiplierBySentence =
            multiplierFnForDate(timelineCalculationDate, allocatedTranche!!.date, offender)
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

  fun isPersonConsideredOutOfCustodyAtTrancheCommencement(timelineCalculationDate: LocalDate, earlyReleaseConfiguration: EarlyReleaseConfiguration, timelineTrackingData: TimelineTrackingData): Boolean {
    with(timelineTrackingData) {
      if (isOutOfPrison() && earlyReleaseConfiguration.earliestTranche() == timelineCalculationDate) {
        // They are out of prison. The following code checking for any exemptions to that.

        // If they were a HDC, ERS or ECSL release then they should not be early released.
        if (listOf(ExternalMovementReason.HDC, ExternalMovementReason.ERS, ExternalMovementReason.ECSL).contains(outOfPrisonStatus!!.release.movementReason)) {
          return true
        }

        // If the person was UAL at tranche commencement then they are subject to early release.
        val ualAtCommencement = previousUalPeriods.any {
          it.first.isBefore(timelineCalculationDate) && it.second.isAfterOrEqualTo(timelineCalculationDate)
        }
        return !ualAtCommencement
      }
      return false
    }
  }

  private fun getPotentialEarlyReleaseSentences(allSentences: List<CalculableSentence>, earlyReleaseConfiguration: EarlyReleaseConfiguration, offender: Offender): List<CalculableSentence> = allSentences.filter { sentence -> sentence.sentenceParts().any { earlyReleaseConfiguration.matchesFilter(it, offender) } }
}
