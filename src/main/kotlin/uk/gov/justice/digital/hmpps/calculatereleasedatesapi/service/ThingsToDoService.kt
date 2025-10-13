package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ToDoType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedBookingAndSentenceAdjustmentAnalysisResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedBookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ThingsToDo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Service
class ThingsToDoService(
  private val adjustmentsService: AdjustmentsService,
  private val prisonService: PrisonService,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val sourceDataMapper: SourceDataMapper,
  private val calculationSourceDataService: CalculationSourceDataService,
) {

  fun getToDoList(prisonerId: String): ThingsToDo {
    val offenderDetails = prisonService.getOffenderDetail(prisonerId)
    val thingsToDo = if (isCalculationRequired(offenderDetails)) {
      listOf(ToDoType.CALCULATION_REQUIRED)
    } else {
      emptyList()
    }

    return ThingsToDo(
      prisonerId = prisonerId,
      thingsToDo = thingsToDo,
    )
  }

  private fun isCalculationRequired(prisonerDetails: PrisonerDetails): Boolean {
    val adjustments = adjustmentsService.getAnalysedBookingAndSentenceAdjustments(prisonerDetails.bookingId)
    val latestConfirmedCalculation = calculationRequestRepository.findFirstByBookingIdAndCalculationStatusOrderByCalculatedAtDesc(bookingId = prisonerDetails.bookingId, status = CalculationStatus.CONFIRMED.name)
    if (!latestConfirmedCalculation.isPresent) {
      // there's never been a calculation so triggering a calculation is required
      return true
    }
    val previousSourceData = sourceDataMapper.getSourceData(latestConfirmedCalculation.get())
    val currentSourceData = calculationSourceDataService.getCalculationSourceData(prisonerDetails, InactiveDataOptions.default())
    return hasNewOrUpdatedSentences(previousSourceData, currentSourceData) ||
      hasNewBookingAdjustments(adjustments) ||
      hasNewSentenceAdjustments(adjustments) ||
      returnToCustodyDateHasChanged(previousSourceData, currentSourceData) ||
      finePaymentsHaveChanged(previousSourceData, currentSourceData)
  }

  private fun hasNewOrUpdatedSentences(previousSourceData: CalculationSourceData, currentSourceData: CalculationSourceData): Boolean = areThereAnyDifferencesInSentencesAndOffences(
    previousSourceData.sentenceAndOffences,
    currentSourceData.sentenceAndOffences,
  )

  private fun areThereAnyDifferencesInSentencesAndOffences(
    previousSentencesAndOffences: List<SentenceAndOffenceWithReleaseArrangements>,
    currentSentencesAndOffences: List<SentenceAndOffenceWithReleaseArrangements>,
  ): Boolean {
    if (previousSentencesAndOffences == currentSentencesAndOffences) {
      return false
    }
    val previousSentenceSequences = previousSentencesAndOffences.map { it.sentenceSequence }.toSet()
    val currentSentenceSequences = currentSentencesAndOffences.map { it.sentenceSequence }.toSet()
    val allSentenceSequences = previousSentenceSequences + currentSentenceSequences
    return allSentenceSequences.any { sentenceSequence ->
      sentenceAndOffenceHasChanged(
        previousSentencesAndOffences.find { it.sentenceSequence == sentenceSequence },
        currentSentencesAndOffences.find { it.sentenceSequence == sentenceSequence },
      )
    }
  }

  private fun sentenceAndOffenceHasChanged(
    previous: SentenceAndOffenceWithReleaseArrangements?,
    current: SentenceAndOffenceWithReleaseArrangements?,
  ): Boolean {
    if (previous == null || current == null) {
      return true
    }
    return isSentenceStillConcurrentOrConsecutiveToTheSameSentence(previous, current) ||
      hasSentenceStatusChanged(previous, current) ||
      hasSentenceTypeChanged(previous, current) ||
      hasSentenceDateChanged(previous, current) ||
      haveSentenceTermsChanged(previous, current) ||
      haveFineAmountsChanged(previous, current)
  }

  private fun isSentenceStillConcurrentOrConsecutiveToTheSameSentence(
    previous: SentenceAndOffenceWithReleaseArrangements,
    current: SentenceAndOffenceWithReleaseArrangements,
  ): Boolean = previous.consecutiveToSequence != current.consecutiveToSequence

  private fun hasSentenceStatusChanged(
    previous: SentenceAndOffenceWithReleaseArrangements,
    current: SentenceAndOffenceWithReleaseArrangements,
  ): Boolean = previous.sentenceStatus != current.sentenceStatus

  private fun hasSentenceTypeChanged(
    previous: SentenceAndOffenceWithReleaseArrangements,
    current: SentenceAndOffenceWithReleaseArrangements,
  ): Boolean = previous.sentenceCalculationType != current.sentenceCalculationType

  private fun hasSentenceDateChanged(
    previous: SentenceAndOffenceWithReleaseArrangements,
    current: SentenceAndOffenceWithReleaseArrangements,
  ): Boolean = previous.sentenceDate != current.sentenceDate

  private fun haveSentenceTermsChanged(
    previous: SentenceAndOffenceWithReleaseArrangements,
    current: SentenceAndOffenceWithReleaseArrangements,
  ): Boolean = previous.terms != current.terms

  private fun haveFineAmountsChanged(
    previous: SentenceAndOffenceWithReleaseArrangements,
    current: SentenceAndOffenceWithReleaseArrangements,
  ): Boolean = previous.fineAmount != current.fineAmount

  private fun hasNewBookingAdjustments(adjustments: AnalysedBookingAndSentenceAdjustments): Boolean = adjustments.bookingAdjustments.any {
    it.analysisResult == AnalysedBookingAndSentenceAdjustmentAnalysisResult.NEW
  }

  private fun hasNewSentenceAdjustments(adjustments: AnalysedBookingAndSentenceAdjustments): Boolean = adjustments.sentenceAdjustments.any {
    it.analysisResult == AnalysedBookingAndSentenceAdjustmentAnalysisResult.NEW
  }

  private fun returnToCustodyDateHasChanged(previousSourceData: CalculationSourceData, currentSourceData: CalculationSourceData): Boolean = previousSourceData.returnToCustodyDate != currentSourceData.returnToCustodyDate

  private fun finePaymentsHaveChanged(previousSourceData: CalculationSourceData, currentSourceData: CalculationSourceData): Boolean = previousSourceData.offenderFinePayments != currentSourceData.offenderFinePayments
}
