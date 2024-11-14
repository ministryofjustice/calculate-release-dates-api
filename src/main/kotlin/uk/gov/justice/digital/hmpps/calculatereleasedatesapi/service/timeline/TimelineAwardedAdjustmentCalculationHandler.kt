package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Term
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleasePointMultiplierLookup
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class TimelineAwardedAdjustmentCalculationHandler(
  trancheConfiguration: SDS40TrancheConfiguration,
  multiplierLookup: ReleasePointMultiplierLookup,
  timelineCalculator: TimelineCalculator,
) : TimelineCalculationHandler(trancheConfiguration, multiplierLookup, timelineCalculator) {
  override fun handle(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData): TimelineHandleResult {
    with(timelineTrackingData) {
      val adas = futureData.additional.filter { it.appliesToSentencesFrom == timelineCalculationDate }
      val adaDays = adas.map { it.numberOfDays }.reduceOrNull { acc, it -> acc + it }?.toLong() ?: 0L
      val radas = futureData.restored.filter { it.appliesToSentencesFrom == timelineCalculationDate }
      val radaDays = radas.map { it.numberOfDays }.reduceOrNull { acc, it -> acc + it }?.toLong() ?: 0L

      if (custodialSentences.isEmpty()) {
        // This is a PADA. No calculation required. Set value here to be applied to later sentences.
        padas += adaDays - radaDays
        return TimelineHandleResult(true)
      } else {
        val maxReleaseNonTermRelease =
          custodialSentences.filterNot { it is Term }.maxOfOrNull { it.sentenceCalculation.releaseDate }
        if (maxReleaseNonTermRelease != null && maxReleaseNonTermRelease.isAfterOrEqualTo(timelineCalculationDate)) {
          timelineCalculator.setAdjustments(
            custodialSentences,
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
