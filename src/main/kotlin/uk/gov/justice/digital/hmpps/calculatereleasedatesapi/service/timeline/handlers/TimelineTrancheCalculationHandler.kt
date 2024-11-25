package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleasePointMultiplierLookup
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SentencesExtractionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.TrancheAllocationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import java.time.LocalDate

@Service
class TimelineTrancheCalculationHandler(
  trancheConfiguration: SDS40TrancheConfiguration,
  multiplierLookup: ReleasePointMultiplierLookup,
  timelineCalculator: TimelineCalculator,
  val trancheAllocationService: TrancheAllocationService,
  val sentenceExtractionService: SentencesExtractionService,
) : TimelineCalculationHandler(trancheConfiguration, multiplierLookup, timelineCalculator) {

  override fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      val allSentences = releasedSentences.map { it.sentences }.plus(listOf(custodialSentences))

      if (isLatestPotentialEarlyReleaseBeforeTrancheOneCommencement(allSentences)) {
        return TimelineHandleResult(true)
      }

      if (custodialSentences.isNotEmpty()) {
        val tranche = trancheAllocationService.calculateTranche(allSentences.flatten())

        val trancheCommencementDate = when (tranche) {
          SDSEarlyReleaseTranche.TRANCHE_1 -> trancheConfiguration.trancheOneCommencementDate
          SDSEarlyReleaseTranche.TRANCHE_2 -> trancheConfiguration.trancheTwoCommencementDate
          SDSEarlyReleaseTranche.TRANCHE_3 -> trancheConfiguration.trancheThreeCommencementDate
          else -> null
        }

        trancheAndCommencement = tranche to trancheCommencementDate

        if (requiresTranchingNow(timelineCalculationDate, timelineTrackingData)) {
          beforeTrancheCalculation = timelineCalculator.getLatestCalculation(allSentences, offender)
          custodialSentences.forEach {
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
          return TimelineHandleResult(true)
        }
      }
    }
    return TimelineHandleResult()
  }

  private fun isLatestPotentialEarlyReleaseBeforeTrancheOneCommencement(allSentences: List<List<CalculableSentence>>): Boolean {
    val earlyReleaseSentences = allSentences.flatten().filter {
      it.sentenceParts().any { part -> part.identificationTrack == SentenceIdentificationTrack.SDS_EARLY_RELEASE }
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
      val anyEarlyRelease = custodialSentences.any { sentence ->
        sentence.sentenceParts().any { it.identificationTrack == SentenceIdentificationTrack.SDS_EARLY_RELEASE }
      }

      return anyEarlyRelease && trancheAndCommencement.second == timelineCalculationDate
    }
  }
}
