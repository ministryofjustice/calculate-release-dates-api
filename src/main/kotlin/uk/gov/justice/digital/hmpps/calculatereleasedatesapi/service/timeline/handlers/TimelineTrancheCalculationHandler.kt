package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ReleasePointMultipliersConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SentencesExtractionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.TrancheAllocationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import java.time.LocalDate

@Service
class TimelineTrancheCalculationHandler(
  trancheConfiguration: SDS40TrancheConfiguration,
  releasePointConfiguration: ReleasePointMultipliersConfiguration,
  timelineCalculator: TimelineCalculator,
  val trancheAllocationService: TrancheAllocationService,
  val sentenceExtractionService: SentencesExtractionService,
) : TimelineCalculationHandler(trancheConfiguration, releasePointConfiguration, timelineCalculator) {

  override fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      val allSentences = releasedSentenceGroups.map { it.sentences }.plus(listOf(currentSentenceGroup))

      if (isLatestPotentialEarlyReleaseBeforeTrancheOneCommencement(allSentences)) {
        return TimelineHandleResult(true)
      }
      val nonRecallSentences = allSentences.flatten().filterNot { it.isRecall() }
      if (nonRecallSentences.isNotEmpty() && inPrison) {
        val tranche = trancheAllocationService.calculateTranche(allSentences.flatten())

        val trancheCommencementDate = when (tranche) {
          SDSEarlyReleaseTranche.TRANCHE_1 -> trancheConfiguration.trancheOneCommencementDate
          SDSEarlyReleaseTranche.TRANCHE_2 -> trancheConfiguration.trancheTwoCommencementDate
          else -> null
        }

        if (trancheCommencementDate == timelineCalculationDate) {
          trancheAndCommencement = tranche to trancheCommencementDate
        }

        if (requiresTranchingNow(timelineCalculationDate, timelineTrackingData)) {
          beforeTrancheCalculation = timelineCalculator.getLatestCalculation(allSentences, offender, timelineTrackingData.returnToCustodyDate)
          currentSentenceGroup.forEach {
            it.sentenceCalculation.unadjustedReleaseDate.findMultiplierByIdentificationTrack =
              multiplierFnForDate(timelineCalculationDate, trancheCommencementDate)
            it.sentenceCalculation.adjustments = it.sentenceCalculation.adjustments.copy(
              unusedAdaDays = 0,
              unusedLicenceAdaDays = 0,
            )
            it.sentenceCalculation.trancheCommencement = trancheCommencementDate
          }
        } else {
          // No sentences at tranche date.
          return TimelineHandleResult(requiresCalculation = false)
        }
      }
    }
    return TimelineHandleResult()
  }

  private fun isLatestPotentialEarlyReleaseBeforeTrancheOneCommencement(allSentences: List<List<CalculableSentence>>): Boolean {
    val earlyReleaseSentences = allSentences.flatten().filter {
      it.sentenceParts().any { part -> part.identificationTrack.isEarlyReleaseTrancheOneTwo() }
    }
    if (earlyReleaseSentences.isNotEmpty()) {
      val latestPotentialEarlyRelease =
        sentenceExtractionService.mostRecent(
          earlyReleaseSentences,
          SentenceCalculation::adjustedDeterminateReleaseDate,
        )
      return latestPotentialEarlyRelease.isBefore(trancheConfiguration.trancheOneCommencementDate)
    }
    return false
  }

  private fun requiresTranchingNow(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData): Boolean {
    with(timelineTrackingData) {
      val anyEarlyRelease = currentSentenceGroup.any { sentence ->
        sentence.sentenceParts().any { it.identificationTrack.isEarlyReleaseTrancheOneTwo() }
      }

      return anyEarlyRelease && trancheAndCommencement.second == timelineCalculationDate
    }
  }
}
