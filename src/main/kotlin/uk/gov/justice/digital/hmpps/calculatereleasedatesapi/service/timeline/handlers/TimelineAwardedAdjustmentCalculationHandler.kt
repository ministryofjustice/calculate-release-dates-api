package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfigurations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Term
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class TimelineAwardedAdjustmentCalculationHandler(
  timelineCalculator: TimelineCalculator,
  earlyReleaseConfigurations: EarlyReleaseConfigurations,
) : TimelineCalculationHandler(timelineCalculator, earlyReleaseConfigurations) {
  override fun handle(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData): TimelineHandleResult {
    with(timelineTrackingData) {
      val adas = futureData.additional.filter { it.appliesToSentencesFrom == timelineCalculationDate }
      val adaDays = adas.map { it.numberOfDays }.reduceOrNull { acc, it -> acc + it }?.toLong() ?: 0L
      val radas = futureData.restored.filter { it.appliesToSentencesFrom == timelineCalculationDate }
      val radaDays = radas.map { it.numberOfDays }.reduceOrNull { acc, it -> acc + it }?.toLong() ?: 0L

      if (currentSentenceGroup.isEmpty()) {
        val ftrSentences = licenceSentences.filter { it.recallType?.isFixedTermRecall == true }
        if (ftrSentences.isNotEmpty() && timelineCalculationDate.isAfterOrEqualTo(timelineTrackingData.returnToCustodyDate!!)) {
          // We are in FTR time
          timelineCalculator.setAdjustments(
            ftrSentences,
            SentenceAdjustments(
              awardedAfterDeterminateRelease = adaDays - radaDays,
            ),
          )
        } else if (licenceSentences.none { it.isRecall() }) {
          // This is a PADA. No calculation required. Set value here to be applied to later sentences.
          padas += adaDays - radaDays
          return TimelineHandleResult(requiresCalculation = false)
        } else {
          // standard recall
          return TimelineHandleResult(requiresCalculation = false)
        }
      } else {
        val maxReleaseNonTermRelease =
          currentSentenceGroup.filterNot { it is Term }.maxOfOrNull { it.sentenceCalculation.releaseDate }
        if (maxReleaseNonTermRelease != null && maxReleaseNonTermRelease.isAfterOrEqualTo(timelineCalculationDate)) {
          timelineCalculator.setAdjustments(
            currentSentenceGroup,
            SentenceAdjustments(
              awardedDuringCustody = adaDays - radaDays,
            ),
          )
        }
      }

      futureData.additional -= adas
      futureData.restored -= radas
    }
    return TimelineHandleResult()
  }
}
