package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ToDoType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ThingsToDo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate

@Service
class ThingsToDoService(
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

  /**
   * Asserts whether an offender requires a new calculation.
   *
   * Rules:
   * - The offender must always have active sentences and offences.
   * - If no calculations exist, a calculation is required.
   * - If any data between the previous and current booking has changed, a calculation is required.
   */
  private fun isCalculationRequired(prisonerDetails: PrisonerDetails): Boolean {
    val currentSourceData =
      calculationSourceDataService.getCalculationSourceData(prisonerDetails, SourceDataLookupOptions.default())

    if (currentSourceData.sentenceAndOffences.isEmpty()) {
      return false
    }

    val latestConfirmedCalculation = calculationRequestRepository.findFirstByBookingIdAndCalculationStatusOrderByCalculatedAtDesc(
      bookingId = prisonerDetails.bookingId,
      status = CalculationStatus.CONFIRMED.name,
    )

    if (!latestConfirmedCalculation.isPresent) {
      return true
    }

    val previousSourceData = sourceDataMapper.getSourceData(latestConfirmedCalculation.get())

    return hasNewOrUpdatedSentences(previousSourceData, currentSourceData) ||
      haveAdjustmentsChanged(previousSourceData, currentSourceData, prisonerDetails) ||
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

  private fun haveAdjustmentsChanged(previousSourceData: CalculationSourceData, currentSourceData: CalculationSourceData, prisonerDetails: PrisonerDetails): Boolean {
    val previousAdjustments = normaliseAdjustments(previousSourceData, prisonerDetails)
    val currentAdjustments = normaliseAdjustments(currentSourceData, prisonerDetails)
    return previousAdjustments != currentAdjustments
  }

  private fun normaliseAdjustments(
    sourceData: CalculationSourceData,
    prisonerDetails: PrisonerDetails,
  ): List<ComparableAdjustment> = sourceData.bookingAndSentenceAdjustments
    .fold({ it.upgrade(prisonerDetails) }, { it })
    .filter { it.status == AdjustmentDto.Status.ACTIVE }
    .map { adjustmentDto ->
      ComparableAdjustment(
        type = adjustmentDto.adjustmentType,
        fromDate = adjustmentDto.fromDate,
        toDate = adjustmentDto.toDate,
        numberOfDays = adjustmentDto.days,
        sentenceSequence = adjustmentDto.sentenceSequence,
      )
    }

  private fun returnToCustodyDateHasChanged(previousSourceData: CalculationSourceData, currentSourceData: CalculationSourceData): Boolean = previousSourceData.returnToCustodyDate != currentSourceData.returnToCustodyDate

  private fun finePaymentsHaveChanged(previousSourceData: CalculationSourceData, currentSourceData: CalculationSourceData): Boolean = previousSourceData.offenderFinePayments != currentSourceData.offenderFinePayments

  private data class ComparableAdjustment(
    val type: AdjustmentDto.AdjustmentType,
    val fromDate: LocalDate?,
    val toDate: LocalDate?,
    val numberOfDays: Int?,
    val sentenceSequence: Int?,
  )
}
