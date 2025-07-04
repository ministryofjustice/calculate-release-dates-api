package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfigurations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
import java.time.LocalDate

@Service
class TimelineUalAdjustmentCalculationHandler(
  timelineCalculator: TimelineCalculator,
  earlyReleaseConfigurations: EarlyReleaseConfigurations,
) : TimelineCalculationHandler(timelineCalculator, earlyReleaseConfigurations) {
  override fun handle(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData): TimelineHandleResult {
    with(timelineTrackingData) {
      val ual = futureData.ual.filter { it.appliesToSentencesFrom == timelineCalculationDate }
      val ualDays = ual.map { it.numberOfDays }.reduceOrNull { acc, it -> acc + it }?.toLong() ?: 0L
      val lastDayOfUal = ual.mapNotNull { it.toDate }.maxOrNull()

      setAdjustmentsDuringCustodialPeriod(timelineCalculationDate, timelineTrackingData, ualDays, lastDayOfUal)

      setAdjustmentsDuringLicencePeriod(timelineCalculationDate, timelineTrackingData, ualDays, lastDayOfUal)

      futureData.ual -= ual
    }
    return TimelineHandleResult()
  }

  private fun setAdjustmentsDuringLicencePeriod(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
    ualDays: Long,
    lastDayOfUal: LocalDate?,
  ) {
    with(timelineTrackingData) {
      val hasFixedTermRecall =
        returnToCustodyDate != null && licenceSentences.any { it.recallType?.isFixedTermRecall == true }
      licenceSentences.filter { it.isRecall() }.forEach { sentence ->
        val ualAfterFtr =
          if (hasFixedTermRecall &&
            timelineCalculationDate.isAfterOrEqualTo(returnToCustodyDate!!) &&
            timelineCalculationDate.isBeforeOrEqualTo(
              sentence.sentenceCalculation.releaseDate,
            )
          ) {
            ualDays
          } else {
            0L
          }
        val sentenceCalculation = sentence.sentenceCalculation
        sentenceCalculation.adjustments = sentenceCalculation.adjustments.copy(
          ualAfterDeterminateRelease = sentenceCalculation.adjustments.ualAfterDeterminateRelease + ualDays,
          ualAfterFtr = sentenceCalculation.adjustments.ualAfterFtr + ualAfterFtr,
        )
        sentenceCalculation.lastDayOfUal = lastDayOfUal
      }
    }
  }

  private fun setAdjustmentsDuringCustodialPeriod(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
    ualDays: Long,
    lastDayOfUal: LocalDate?,
  ) {
    with(timelineTrackingData) {
      currentSentenceGroup.forEach {
        it.sentenceCalculation.lastDayOfUal = lastDayOfUal
      }
      val (sentencesBeforeReleaseDate, sentencesAfterReleaseDate) = currentSentenceGroup.partition { timelineCalculationDate.isBeforeOrEqualTo(it.sentenceCalculation.adjustedDeterminateReleaseDate) }
      timelineCalculator.setAdjustments(
        sentencesBeforeReleaseDate,
        SentenceAdjustments(
          ualDuringCustody = ualDays,
        ),
      )
      timelineCalculator.setAdjustments(
        sentencesAfterReleaseDate.filter { it.isRecall() },
        SentenceAdjustments(
          ualAfterDeterminateRelease = ualDays,
        ),
      )
    }
  }
}
