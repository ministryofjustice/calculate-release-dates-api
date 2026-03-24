package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.ApplicableLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.FTRLegislations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.TrancheAllocationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class TimelineFTR56TrancheCalculationHandler(
  timelineCalculator: TimelineCalculator,
  val ftrLegislations: FTRLegislations,
  val trancheAllocationService: TrancheAllocationService,
) : AbstractTimelineTrancheHandler(timelineCalculator) {

  override fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      val ftr56Legislation = ftrLegislations.ftr56Legislation
      val earlyReleaseConfiguration = ftr56Legislation.configuration
      val thisTranche = requireNotNull(earlyReleaseConfiguration.tranches.find { it.date == timelineCalculationDate }) { "Couldn't find tranche for date $timelineCalculationDate" }

      if (isPersonConsideredOutOfCustodyAtTrancheCommencement(timelineCalculationDate, ftr56Legislation.commencementDate(), timelineTrackingData)) {
        // The person is considered out of custody and is excluded from early release.
        return TimelineHandleResult(false)
      }

      if (applicableFtrLegislation == null) {
        val allocatedTranche = trancheAllocationService.allocateTranche(timelineTrackingData, ftr56Legislation)
        if (allocatedTranche != null && allocatedTranche.date.isAfterOrEqualTo(timelineCalculationDate)) {
          applicableFtrLegislation = ApplicableLegislation(
            legislation = ftr56Legislation,
            earliestApplicableDate = allocatedTranche.date,
          )
          allocatedTranche.name?.let { name -> trancheAllocationByCategory[name.category] = name }
        } else if (thisTranche.type == EarlyReleaseTrancheType.FINAL) {
          // after the final tranche the legislation is in full effect and should apply to all future eligible recalls with no defaulting required
          applicableFtrLegislation = ApplicableLegislation(
            legislation = ftr56Legislation,
            earliestApplicableDate = null,
          )
        }
      }

      val thisTrancheIsTheOneAllocated = applicableFtrLegislation?.earliestApplicableDate == thisTranche.date
      val sentencesToModifyReleaseDates = sentencesToModifyReleaseDates(timelineTrackingData, timelineCalculationDate, earlyReleaseConfiguration)
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
    earlyReleaseConfiguration: EarlyReleaseConfiguration,
  ): List<CalculableSentence> = (timelineTrackingData.currentSentenceGroup + timelineTrackingData.licenceSentences).filter {
    it.sentenceCalculation.releaseDate.isAfter(timelineCalculationDate)
  }
    .filter { sentence -> sentence.sentenceParts().any { earlyReleaseConfiguration.matchesFilter(it) } }
}
