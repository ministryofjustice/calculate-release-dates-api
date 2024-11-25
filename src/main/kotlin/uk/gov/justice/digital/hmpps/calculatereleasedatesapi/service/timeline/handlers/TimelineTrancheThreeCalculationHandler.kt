package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleasePointMultiplierLookup
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.t3offenceCodes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class TimelineTrancheThreeCalculationHandler(
  trancheConfiguration: SDS40TrancheConfiguration,
  multiplierLookup: ReleasePointMultiplierLookup,
  timelineCalculator: TimelineCalculator,
) : TimelineCalculationHandler(trancheConfiguration, multiplierLookup, timelineCalculator) {

  override fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      custodialSentences
        .filter { it.offence.offenceCode in t3offenceCodes }
        .filter { it.identificationTrack === SentenceIdentificationTrack.SDS_EARLY_RELEASE }
        .filter { it.sentenceCalculation.adjustedHistoricDeterminateReleaseDate.isAfterOrEqualTo(trancheConfiguration.trancheThreeCommencementDate) }
        .forEach { calculableSentence ->
          calculableSentence.identificationTrack = SentenceIdentificationTrack.SDS_STANDARD_RELEASE_T3_EXCLUSION

          calculableSentence.sentenceCalculation.unadjustedReleaseDate.findMultiplierByIdentificationTrack =
            multiplierFnForDate(timelineCalculationDate, trancheAndCommencement.second)
        }
    }
    return TimelineHandleResult()
  }
}
