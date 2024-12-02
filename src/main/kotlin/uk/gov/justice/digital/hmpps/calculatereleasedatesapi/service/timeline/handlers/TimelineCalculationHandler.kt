package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleasePointMultiplierLookup
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

abstract class TimelineCalculationHandler(
  protected val trancheConfiguration: SDS40TrancheConfiguration,
  protected val multiplierLookup: ReleasePointMultiplierLookup,
  protected val timelineCalculator: TimelineCalculator,
) {
  abstract fun handle(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData): TimelineHandleResult

  /**
   * Return lambda function which receives the sentence track and returns the
   * sentence multiplier for use when calculating the earliest release date.
   *
   * 1. If timelineCalculationDate if on or after tranche three commencement date and
   *    the track is SDS_STANDARD_RELEASE_T3_EXCLUSION, then return default SDS_STANDARD multiplier,
   *    as related offence is now excluded from early release
   * 2. If timelineCalculationDate date is on or after trancheOneCommencementDate, return multiplier which
   *    may be eligible for early release (early release scheme commenced as of tranche one)
   * 3. Default to historic multipliers, which are pre early release
   */
  fun multiplierFnForDate(
    timelineCalculationDate: LocalDate,
    earlyReleaseCommencementDate: LocalDate?,
  ): (identification: SentenceIdentificationTrack) -> Double {
    if (timelineCalculationDate.isAfterOrEqualTo(trancheConfiguration.trancheThreeCommencementDate)) {
      return { identification: SentenceIdentificationTrack ->
        if (identification == SentenceIdentificationTrack.SDS_STANDARD_RELEASE_T3_EXCLUSION) {
          multiplierLookup.historicMultiplierFor(identification)
        } else {
          multiplierLookup.multiplierFor(identification)
        }
      }
    }

    if (timelineCalculationDate.isAfterOrEqualTo(earlyReleaseCommencementDate ?: trancheConfiguration.trancheOneCommencementDate)) {
      return { identification: SentenceIdentificationTrack -> multiplierLookup.multiplierFor(identification) }
    }

    return { identification: SentenceIdentificationTrack -> multiplierLookup.historicMultiplierFor(identification) }
  }

  fun historicMultiplierFnForDate(): (identification: SentenceIdentificationTrack) -> Double {
    return { identification: SentenceIdentificationTrack -> multiplierLookup.historicMultiplierFor(identification) }
  }
}
