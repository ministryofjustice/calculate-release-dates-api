package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ReleasePointMultipliersConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.Constants
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

abstract class TimelineCalculationHandler(
  protected val trancheConfiguration: SDS40TrancheConfiguration,
  private val releasePointConfiguration: ReleasePointMultipliersConfiguration,
  protected val timelineCalculator: TimelineCalculator,
) {
  abstract fun handle(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData): TimelineHandleResult

  fun multiplierFnForDate(
    timelineCalculationDate: LocalDate,
    earlyReleaseCommencementDate: LocalDate?,
  ): (identification: SentenceIdentificationTrack) -> Double = { identification -> multiplierForIdentification(timelineCalculationDate, earlyReleaseCommencementDate, identification) }

  /**
   Historic release point is before SDS40 tranching started.
   */
  fun historicMultiplierFnForDate(): (identification: SentenceIdentificationTrack) -> Double = { identification -> multiplierForIdentification(trancheConfiguration.trancheOneCommencementDate.minusDays(1), null, identification) }

  private fun multiplierForIdentification(
    timelineCalculationDate: LocalDate,
    earlyReleaseCommencementDate: LocalDate?,
    identification: SentenceIdentificationTrack,
  ): Double = if (identification.isMultiplierFixed()) {
    identification.fixedMultiplier()
  } else {
    earlyReleaseMultiplier(timelineCalculationDate, earlyReleaseCommencementDate, identification)
  }

  private fun earlyReleaseMultiplier(timelineCalculationDate: LocalDate, earlyReleaseCommencementDate: LocalDate?, identification: SentenceIdentificationTrack): Double = if (timelineCalculationDate.isAfterOrEqualTo(trancheConfiguration.trancheThreeCommencementDate) && identification == SentenceIdentificationTrack.SDS_STANDARD_RELEASE_T3_EXCLUSION) {
    Constants.HALF
  } else if (timelineCalculationDate.isAfterOrEqualTo(
      earlyReleaseCommencementDate ?: trancheConfiguration.trancheOneCommencementDate,
    )
  ) {
    releasePointConfiguration.earlyReleasePoint
  } else {
    Constants.HALF
  }
}
