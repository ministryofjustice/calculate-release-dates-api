package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleasePointMultiplierLookup
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

abstract class TimelineCalculationHandler(
  protected val trancheConfiguration: SDS40TrancheConfiguration,
  protected val multiplierLookup: ReleasePointMultiplierLookup,
  protected val timelineCalculator: TimelineCalculator,
) {
  abstract fun handle(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData): TimelineHandleResult

  fun multiplerFnForDate(
    timelineCalculationDate: LocalDate,
    earlyReleaseCommencementDate: LocalDate?,
  ): (identification: SentenceIdentificationTrack) -> Double {
    if (timelineCalculationDate.isAfterOrEqualTo(earlyReleaseCommencementDate ?: trancheConfiguration.trancheOneCommencementDate)) {
      return { identification: SentenceIdentificationTrack -> multiplierLookup.multiplierFor(identification) }
    }
    return { identification: SentenceIdentificationTrack -> multiplierLookup.historicMultiplierFor(identification) }
  }
}
