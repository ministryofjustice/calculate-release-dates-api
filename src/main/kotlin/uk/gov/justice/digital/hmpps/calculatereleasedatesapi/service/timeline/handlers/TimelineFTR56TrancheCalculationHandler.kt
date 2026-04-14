package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.ApplicableLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.TrancheAllocationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculationEvent.FTR56TrancheTimelineCalculationEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class TimelineFTR56TrancheCalculationHandler(
  timelineCalculator: TimelineCalculator,
  val trancheAllocationService: TrancheAllocationService,
) : TimelineCalculationHandler<FTR56TrancheTimelineCalculationEvent>(timelineCalculator) {

  override fun handle(
    event: FTR56TrancheTimelineCalculationEvent,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      if (applicableFtrLegislation == null) {
        val allocatedTranche = trancheAllocationService.allocateTranche(timelineTrackingData, event.legislation)
        if (allocatedTranche != null && allocatedTranche.date.isAfterOrEqualTo(event.date)) {
          applicableFtrLegislation = ApplicableLegislation(
            legislation = event.legislation,
            earliestApplicableDate = allocatedTranche.date,
          )
          trancheAllocationByLegislationName[event.legislation.legislationName] = allocatedTranche.name
        } else {
          // if no tranche is applicable then the legislation becomes available for all FTR56 sentences from this date
          applicableFtrLegislation = ApplicableLegislation(
            legislation = event.legislation,
            earliestApplicableDate = null,
          )
        }
      }

      val thisTrancheIsTheOneAllocated = applicableFtrLegislation?.earliestApplicableDate == event.tranche.date
      val sentencesToModifyReleaseDates = sentencesToModifyReleaseDates(timelineTrackingData, event.date)
      if (thisTrancheIsTheOneAllocated || sentencesToModifyReleaseDates.isNotEmpty()) {
        sentencesToModifyReleaseDates.forEach {
          it.sentenceCalculation.unadjustedReleaseDate.calculationTrigger = it.sentenceCalculation.unadjustedReleaseDate.calculationTrigger.copy(ftr56Supported = true)
          it.sentenceCalculation.applicableFtrLegislation = applicableFtrLegislation
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
  ): List<CalculableSentence> = (timelineTrackingData.currentSentenceGroup + timelineTrackingData.licenceSentences).filter {
    it.sentenceCalculation.releaseDate.isAfter(timelineCalculationDate)
  }
    .filter { sentence -> sentence.sentenceParts().any { it.recall?.recallType == RecallType.FIXED_TERM_RECALL_56 } }
}
