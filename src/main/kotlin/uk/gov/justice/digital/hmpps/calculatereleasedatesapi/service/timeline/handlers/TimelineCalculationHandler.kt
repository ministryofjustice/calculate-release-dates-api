package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfigurations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.rmi.UnexpectedException
import java.time.LocalDate

abstract class TimelineCalculationHandler(
  protected val timelineCalculator: TimelineCalculator,
  protected val earlyReleaseConfigurations: EarlyReleaseConfigurations,
) {
  abstract fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult

  fun multiplierFnForDate(
    timelineCalculationDate: LocalDate,
    allocatedTrancheDate: LocalDate?,
    offender: Offender,
  ): (sentence: CalculableSentence) -> Double = { sentence ->
    multiplerForSentence(
      timelineCalculationDate,
      allocatedTrancheDate,
      sentence,
      offender,
    )
  }

  /**
   Historic release point is before SDS40 tranching started
   */
  fun historicMultiplierFnForDate(
    offender: Offender,
  ): (sentence: CalculableSentence) -> Double = { sentence ->
    multiplerForSentence(
      earlyReleaseConfigurations.configurations.minOf { it.earliestTranche() }.minusDays(1),
      null,
      sentence,
      offender,
    )
  }

  private fun multiplerForSentence(
    timelineCalculationDate: LocalDate,
    allocatedTrancheDate: LocalDate?,
    sentence: CalculableSentence,
    offender: Offender,
  ): Double = if (sentence.identificationTrack.isMultiplierFixed()) {
    sentence.identificationTrack.fixedMultiplier()
  } else {
    sdsReleaseMultiplier(sentence, timelineCalculationDate, allocatedTrancheDate, offender)
  }

  private fun sdsReleaseMultiplier(
    sentence: CalculableSentence,
    timelineCalculationDate: LocalDate,
    allocatedTrancheDate: LocalDate?,
    offender: Offender,
  ): Double {
    if (sentence is StandardDeterminateSentence) {
      val latestEarlyReleaseConfig =
        earlyReleaseConfigurations.configurations
          .filter { timelineCalculationDate.isAfterOrEqualTo(it.earliestTranche()) }
          .maxByOrNull { it.earliestTranche() }
      if (latestEarlyReleaseConfig != null) {
        // They are tranched.
        if (allocatedTrancheDate != null) {
          if (latestEarlyReleaseConfig.matchesFilter(sentence, offender)) {
            return getMultiplerForConfiguration(latestEarlyReleaseConfig, timelineCalculationDate, sentence)
          }
        } else if (sentence.sentencedAt.isAfterOrEqualTo(latestEarlyReleaseConfig.earliestTranche())) {
          if (latestEarlyReleaseConfig.matchesFilter(sentence, offender)) {
            return getMultiplerForConfiguration(latestEarlyReleaseConfig, timelineCalculationDate, sentence)
          }
        }
      }
    }
    return defaultSDSReleaseMultiplier(sentence)
  }

  private fun defaultSDSReleaseMultiplier(sentence: CalculableSentence): Double = when (sentence.identificationTrack) {
    SentenceIdentificationTrack.SDS -> 0.5
    SentenceIdentificationTrack.SDS_PLUS -> 2.toDouble().div(3)
    else -> throw UnexpectedException("Unknown default release multipler.")
  }

  private fun getMultiplerForConfiguration(
    earlyReleaseConfig: EarlyReleaseConfiguration,
    timelineCalculationDate: LocalDate,
    sentence: StandardDeterminateSentence,
  ): Double {
    val sds40Tranche3 = earlyReleaseConfig.tranches.find { it.type == EarlyReleaseTrancheType.SDS_40_TRANCHE_3 }
    if (sds40Tranche3 != null && timelineCalculationDate.isAfterOrEqualTo(sds40Tranche3.date) && sentence.hasAnSDSEarlyReleaseExclusion.trancheThreeExclusion) {
      return defaultSDSReleaseMultiplier(sentence)
    }
    return earlyReleaseConfig.releaseMultiplier[sentence.identificationTrack]!!.toDouble()
  }
}
