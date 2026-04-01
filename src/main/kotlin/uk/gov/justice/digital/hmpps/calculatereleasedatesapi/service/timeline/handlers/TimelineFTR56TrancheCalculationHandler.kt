package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.ApplicableLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.FTRLegislationConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.TrancheAllocationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class TimelineFTR56TrancheCalculationHandler(
  timelineCalculator: TimelineCalculator,
  val ftrLegislationConfiguration: FTRLegislationConfiguration,
  val trancheAllocationService: TrancheAllocationService,
) : TimelineCalculationHandler(timelineCalculator) {

  override fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      val ftr56Legislation = ftrLegislationConfiguration.ftr56Legislation
      val thisTranche = requireNotNull(ftr56Legislation.tranches.find { it.date == timelineCalculationDate }) { "Couldn't find tranche for date $timelineCalculationDate" }

      if (applicableFtrLegislation == null) {
        val allocatedTranche = trancheAllocationService.allocateTranche(timelineTrackingData, ftr56Legislation)
        if (allocatedTranche != null && allocatedTranche.date.isAfterOrEqualTo(timelineCalculationDate)) {
          applicableFtrLegislation = ApplicableLegislation(
            legislation = ftr56Legislation,
            earliestApplicableDate = allocatedTranche.date,
          )
          trancheAllocationByLegislationName[ftr56Legislation.legislationName] = allocatedTranche.name
        } else {
          // if no tranche is applicable then the legislation becomes available for all FTR56 sentences from this date
          applicableFtrLegislation = ApplicableLegislation(
            legislation = ftr56Legislation,
            earliestApplicableDate = null,
          )
        }
      }

      val thisTrancheIsTheOneAllocated = applicableFtrLegislation?.earliestApplicableDate == thisTranche.date
      val sentencesToModifyReleaseDates = sentencesToModifyReleaseDates(timelineTrackingData, timelineCalculationDate)
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
