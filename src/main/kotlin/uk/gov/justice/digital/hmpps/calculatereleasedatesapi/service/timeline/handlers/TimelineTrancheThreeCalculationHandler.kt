package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ReleasePointMultipliersConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import java.time.LocalDate

@Service
class TimelineTrancheThreeCalculationHandler(
  trancheConfiguration: SDS40TrancheConfiguration,
  releasePointConfiguration: ReleasePointMultipliersConfiguration,
  timelineCalculator: TimelineCalculator,
) : TimelineCalculationHandler(trancheConfiguration, releasePointConfiguration, timelineCalculator) {

  override fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      currentSentenceGroup
        .filter { sentence -> sentence.sentenceParts().any { it.identificationTrack == SentenceIdentificationTrack.SDS_STANDARD_RELEASE_T3_EXCLUSION } }
        .forEach {
          it.sentenceCalculation.unadjustedReleaseDate.findMultiplierByIdentificationTrack =
            multiplierFnForDate(timelineCalculationDate, timelineTrackingData.trancheAndCommencement.second)
        }
    }
    return TimelineHandleResult()
  }
}
