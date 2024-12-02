package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleasePointMultiplierLookup
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
import java.time.LocalDate

@Service
class TimelineUalAdjustmentCalculationHandler(
  trancheConfiguration: SDS40TrancheConfiguration,
  multiplierLookup: ReleasePointMultiplierLookup,
  timelineCalculator: TimelineCalculator,
) : TimelineCalculationHandler(trancheConfiguration, multiplierLookup, timelineCalculator) {
  override fun handle(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData): TimelineHandleResult {
    with(timelineTrackingData) {
      val ual = futureData.ual.filter { it.appliesToSentencesFrom == timelineCalculationDate }
      val ualDays = ual.map { it.numberOfDays }.reduceOrNull { acc, it -> acc + it }?.toLong() ?: 0L

      setAdjustmentsDuringCustodialPeriod(timelineCalculationDate, timelineTrackingData, ualDays)

      setAdjustmentsDuringLicensePeriod(timelineCalculationDate, timelineTrackingData, ualDays)

      futureData.ual -= ual
    }
    return TimelineHandleResult()
  }

  private fun setAdjustmentsDuringLicensePeriod(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData, ualDays: Long) {
    with(timelineTrackingData) {
      val hasFixedTermRecall =
        returnToCustodyDate != null && licenseSentences.any { it.recallType?.isFixedTermRecall == true }
      licenseSentences.forEach { sentence ->
        val ualAfterFtr =
          if (hasFixedTermRecall && timelineCalculationDate.isAfterOrEqualTo(returnToCustodyDate!!) && timelineCalculationDate.isBeforeOrEqualTo(
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
      }
    }
  }

  private fun setAdjustmentsDuringCustodialPeriod(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData, ualDays: Long) {
    with(timelineTrackingData) {
      val (sentencesBeforeReleaseDate, sentencesAfterReleaseDate) = custodialSentences.partition { timelineCalculationDate.isBeforeOrEqualTo(it.sentenceCalculation.adjustedDeterminateReleaseDate) }
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
