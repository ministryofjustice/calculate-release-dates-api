package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfigurations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.Constants
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

abstract class TimelineCalculationHandler(
  protected val timelineCalculator: TimelineCalculator,
  protected val earlyReleaseConfigurations: EarlyReleaseConfigurations
) {
  abstract fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData
  ): TimelineHandleResult

  fun multiplierFnForDate(
    timelineCalculationDate: LocalDate,
    allocatedTrancheDate: LocalDate?,
  ): (sentence: AbstractSentence) -> Double = { sentence ->
    multiplerForSentence(
      timelineCalculationDate,
      allocatedTrancheDate,
      sentence
    )
  }

  /**
  Historic release point is before SDS40 tranching started
  TODO Check we still need this.
   */
  fun historicMultiplierFnForDate(): (sentence: AbstractSentence) -> Double = { sentence ->
    multiplerForSentence(
      LocalDate.of(2024, 1, 1), //TODO is this needed?
      null,
      sentence
    )
  }

  private fun multiplerForSentence(
    timelineCalculationDate: LocalDate,
    allocatedTrancheDate: LocalDate?,
    sentence: AbstractSentence,
  ): Double = if (sentence.identificationTrack.isMultiplierFixed()) {
    sentence.identificationTrack.fixedMultiplier()
  } else {
    sdsReleaseMultiplier(sentence, timelineCalculationDate, allocatedTrancheDate)
  }

  //TODO how to get SDS40 T3 in here?
  private fun sdsReleaseMultiplier(
    sentence: AbstractSentence,
    timelineCalculationDate: LocalDate,
    allocatedTrancheDate: LocalDate?): Double {
    if (allocatedTrancheDate != null) {
      //They are tranched.
      val earlyReleaseConfig = earlyReleaseConfigurations.configurations.find { it.tranches.any { tranche -> tranche.date == allocatedTrancheDate } }
      if (earlyReleaseConfig!!.matchesFilter(sentence)) {
        return earlyReleaseConfig.releaseMultiplier
      }
    } else {
      val sentencedAfterEarlyReleaseConfig = earlyReleaseConfigurations.configurations.maxByOrNull { sentence.sentencedAt.isAfter(it.earliestTranche()) }
      if (sentencedAfterEarlyReleaseConfig != null) {
        if (sentencedAfterEarlyReleaseConfig.matchesFilter(sentence)) {
          return sentencedAfterEarlyReleaseConfig.releaseMultiplier
        }
      }
    }
    return 0.5
  }
  }
