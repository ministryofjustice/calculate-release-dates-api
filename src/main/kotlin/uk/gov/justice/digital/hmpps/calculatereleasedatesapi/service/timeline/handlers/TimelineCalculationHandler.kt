package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ReleasePointMultipliersConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
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
    return { identification -> multiplierForIdentification(timelineCalculationDate, earlyReleaseCommencementDate, identification) }
  }

  /**
   Historic release point is before SDS40 tranching started.
   */
  fun historicMultiplierFnForDate(): (identification: SentenceIdentificationTrack) -> Double {
    return { identification -> multiplierForIdentification(trancheConfiguration.trancheOneCommencementDate.minusDays(1), null, identification) }
  }

  private fun multiplierForIdentification(
    timelineCalculationDate: LocalDate,
    earlyReleaseCommencementDate: LocalDate?,
    identification: SentenceIdentificationTrack,
  ): Double {
    return when (identification) {
      SentenceIdentificationTrack.EDS_AUTOMATIC_RELEASE,
      SentenceIdentificationTrack.SDS_TWO_THIRDS_RELEASE,
      SentenceIdentificationTrack.SDS_PLUS_RELEASE,
      -> TWO_THIRDS

      SentenceIdentificationTrack.EDS_DISCRETIONARY_RELEASE,
      SentenceIdentificationTrack.SOPC_PED_AT_TWO_THIRDS,
      SentenceIdentificationTrack.SOPC_PED_AT_HALFWAY,
      SentenceIdentificationTrack.AFINE_ARD_AT_FULL_TERM,
      SentenceIdentificationTrack.BOTUS,
      SentenceIdentificationTrack.BOTUS_WITH_HISTORIC_TUSED,
      -> FULL

      SentenceIdentificationTrack.SDS_EARLY_RELEASE,
      SentenceIdentificationTrack.SDS_STANDARD_RELEASE_T3_EXCLUSION,
      -> earlyReleaseMultiplier(timelineCalculationDate, earlyReleaseCommencementDate, identification)

      else -> HALF
    }
  }

  private fun earlyReleaseMultiplier(timelineCalculationDate: LocalDate, earlyReleaseCommencementDate: LocalDate?, identification: SentenceIdentificationTrack): Double {
    return if (timelineCalculationDate.isAfterOrEqualTo(trancheConfiguration.trancheThreeCommencementDate) && identification == SentenceIdentificationTrack.SDS_STANDARD_RELEASE_T3_EXCLUSION) {
      HALF
    } else if (timelineCalculationDate.isAfterOrEqualTo(
        earlyReleaseCommencementDate ?: trancheConfiguration.trancheOneCommencementDate,
      )
    ) {
      releasePointConfiguration.earlyReleasePoint
    } else {
      HALF
    }
  }

  companion object {
    private const val HALF = 1.toDouble().div(2)
    private const val TWO_THIRDS = 2.toDouble().div(3)
    private const val FULL = 1.toDouble()
  }
}
