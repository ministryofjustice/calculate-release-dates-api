package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ReleasePointMultipliersConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UnadjustedReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineHandleResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineTrackingData
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class TimelineSentenceCalculationHandler(
  trancheConfiguration: SDS40TrancheConfiguration,
  releasePointConfiguration: ReleasePointMultipliersConfiguration,
  timelineCalculator: TimelineCalculator,
) : TimelineCalculationHandler(trancheConfiguration, releasePointConfiguration, timelineCalculator) {

  override fun handle(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ): TimelineHandleResult {
    with(timelineTrackingData) {
      inPrison = true
      val newlySentenced = futureData.sentences.filter { it.sentencedAt == timelineCalculationDate }

      shareAdjustmentsFromExistingCustodialSentencesToNewlySentenced(timelineCalculationDate, timelineTrackingData, newlySentenced)

      handleNewlySentencedPartOfConsecutiveSentence(timelineCalculationDate, timelineTrackingData)

      padas = 0
      currentSentenceGroup.addAll(newlySentenced)
      futureData.sentences -= newlySentenced

      shareDeductionsThatAreApplicableToThisSentenceDate(timelineCalculationDate, timelineTrackingData)
    }
    return TimelineHandleResult()
  }

  private fun handleNewlySentencedPartOfConsecutiveSentence(timelineCalculationDate: LocalDate, timelineTrackingData: TimelineTrackingData) {
    with(timelineTrackingData) {
      // This new sentence is part of a consecutive sentence starting on an earlier date.
      currentSentenceGroup.filter {
        it.sentencedAt != timelineCalculationDate && it.sentenceParts()
          .any { part -> part.sentencedAt == timelineCalculationDate }
      }
        .forEach {
          it.sentenceCalculation.unadjustedReleaseDate.findMultiplierByIdentificationTrack =
            multiplierFnForDate(timelineCalculationDate.minusDays(1), trancheAndCommencement.second) // Use day before sentencing for when someone is sentenced for another consecutive part on tranche commencement.
        }
    }
  }

  private fun shareDeductionsThatAreApplicableToThisSentenceDate(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
  ) {
    with(timelineTrackingData) {
      val taggedBail = futureData.taggedBail.filter { it.appliesToSentencesFrom == timelineCalculationDate }
      val remand = futureData.remand.filter { it.appliesToSentencesFrom == timelineCalculationDate }
      val recallTaggedBail = futureData.recallTaggedBail.filter { it.appliesToSentencesFrom == timelineCalculationDate }
      val recallRemand = futureData.recallRemand.filter { it.appliesToSentencesFrom == timelineCalculationDate }
      timelineCalculator.setAdjustments(
        currentSentenceGroup,
        SentenceAdjustments(
          taggedBail = taggedBail.map { it.numberOfDays }.reduceOrNull { acc, it -> acc + it }?.toLong() ?: 0L,
          remand = remand.map { it.numberOfDays }.reduceOrNull { acc, it -> acc + it }?.toLong() ?: 0L,
          recallTaggedBail = recallTaggedBail.map { it.numberOfDays }.reduceOrNull { acc, it -> acc + it }?.toLong()
            ?: 0L,
          recallRemand = recallRemand.map { it.numberOfDays }.reduceOrNull { acc, it -> acc + it }?.toLong() ?: 0L,
        ),
      )

      futureData.taggedBail -= taggedBail
      futureData.remand -= remand
      futureData.recallTaggedBail -= recallTaggedBail
      futureData.recallRemand -= recallRemand
    }
  }

  private fun shareAdjustmentsFromExistingCustodialSentencesToNewlySentenced(
    timelineCalculationDate: LocalDate,
    timelineTrackingData: TimelineTrackingData,
    newlySentenced: List<CalculableSentence>,
  ) {
    with(timelineTrackingData) {
      val existingAdjustments =
        currentSentenceGroup.firstOrNull()?.sentenceCalculation?.adjustments ?: SentenceAdjustments()
      newlySentenced.forEach { sentence ->
        sentence.sentenceCalculation = SentenceCalculation(
          UnadjustedReleaseDate(
            sentence,
            multiplierFnForDate(timelineCalculationDate, trancheAndCommencement.second),
            historicMultiplierFnForDate(),
            returnToCustodyDate,
          ),
          SentenceAdjustments(),
          calculateErsed = options.calculateErsed,
        )
      }
      // New sentence. Find any served adas.
      val servedAdas = findServedAdas(timelineCalculationDate, currentSentenceGroup, latestRelease)
      timelineCalculator.setAdjustments(
        newlySentenced,
        SentenceAdjustments(
          taggedBail = existingAdjustments.taggedBail,
          remand = existingAdjustments.remand,
          recallRemand = existingAdjustments.recallRemand,
          recallTaggedBail = existingAdjustments.recallTaggedBail,
          awardedDuringCustody = padas + existingAdjustments.awardedDuringCustody,
          servedAdaDays = servedAdas,
        ),
      )
    }
  }

  private fun findServedAdas(
    sentenceDate: LocalDate,
    custodialSentences: MutableList<CalculableSentence>,
    latestRelease: Pair<LocalDate, CalculableSentence>,
  ): Long {
    val releaseWithoutAdas = latestRelease.second.sentenceCalculation.releaseDateWithoutAwarded
    if (custodialSentences.isNotEmpty() && sentenceDate.isAfter(releaseWithoutAdas)) {
      return ChronoUnit.DAYS.between(releaseWithoutAdas, sentenceDate) - 1
    }
    return 0
  }
}
