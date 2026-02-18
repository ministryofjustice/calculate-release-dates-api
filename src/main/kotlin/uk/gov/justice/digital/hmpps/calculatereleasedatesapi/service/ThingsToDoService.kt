package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
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
    val check = isCalculationRequired(offenderDetails)
    val thingsToDo = if (check.isCalculationRequired) {
      logger.info("Calculation required for ${offenderDetails.offenderNo} because ${check.reason}")
      listOf(ToDoType.CALCULATION_REQUIRED)
    } else {
      logger.info("Calculation not required for ${offenderDetails.offenderNo} because ${check.reason}")
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
  private fun isCalculationRequired(prisonerDetails: PrisonerDetails): CalculationCheckResult {
    val currentSourceData =
      calculationSourceDataService.getCalculationSourceData(prisonerDetails, SourceDataLookupOptions.default())

    if (currentSourceData.sentenceAndOffences.isEmpty()) {
      return CalculationCheckResult(false, "No sentences and offences on booking")
    }

    val latestConfirmedCalculation = calculationRequestRepository.findFirstByBookingIdAndCalculationStatusOrderByCalculatedAtDesc(
      bookingId = prisonerDetails.bookingId,
      status = CalculationStatus.CONFIRMED.name,
    )

    if (!latestConfirmedCalculation.isPresent) {
      return CalculationCheckResult(true, "No previous calculation found")
    }

    val previousSourceData = sourceDataMapper.getSourceData(latestConfirmedCalculation.get())

    if (hasNewOrUpdatedSentences(previousSourceData, currentSourceData)) {
      return CalculationCheckResult(true, "Has new or updated sentences")
    }
    if (haveAdjustmentsChanged(previousSourceData, currentSourceData, prisonerDetails)) {
      return CalculationCheckResult(true, "Adjustments have changed")
    }
    if (returnToCustodyDateHasChanged(previousSourceData, currentSourceData)) {
      return CalculationCheckResult(true, "Return to custody date has changed")
    }
    if (finePaymentsHaveChanged(previousSourceData, currentSourceData)) {
      return CalculationCheckResult(true, "Fine payments have changed")
    }
    return CalculationCheckResult(false, "Nothing has changed since the previous calculation")
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
    val setOfPreviousAdjustments = normaliseAdjustments(previousSourceData, prisonerDetails)
    val setOfCurrentAdjustments = normaliseAdjustments(currentSourceData, prisonerDetails)
    return setOfPreviousAdjustments != setOfCurrentAdjustments
  }

  private fun normaliseAdjustments(
    sourceData: CalculationSourceData,
    prisonerDetails: PrisonerDetails,
  ): Set<ComparableAdjustment> = sourceData.bookingAndSentenceAdjustments
    .fold({ it.upgrade(prisonerDetails) }, { it })
    .filter { it.status == AdjustmentDto.Status.ACTIVE }
    .map { adjustmentDto ->
      ComparableAdjustment(
        type = adjustmentDto.adjustmentType,
        fromDate = adjustmentDto.fromDate,
        numberOfDays = adjustmentDto.days,
        sentenceSequence = adjustmentDto.sentenceSequence,
      )
    }.toSet()

  private fun returnToCustodyDateHasChanged(previousSourceData: CalculationSourceData, currentSourceData: CalculationSourceData): Boolean = previousSourceData.returnToCustodyDate != currentSourceData.returnToCustodyDate

  private fun finePaymentsHaveChanged(previousSourceData: CalculationSourceData, currentSourceData: CalculationSourceData): Boolean = previousSourceData.offenderFinePayments != currentSourceData.offenderFinePayments

  private data class ComparableAdjustment(
    val type: AdjustmentDto.AdjustmentType,
    val fromDate: LocalDate?,
    val numberOfDays: Int?,
    val sentenceSequence: Int?,
  )

  private data class CalculationCheckResult(val isCalculationRequired: Boolean, val reason: String)

  companion object {
    private val logger = LoggerFactory.getLogger(ThingsToDoService::class.java)
  }
}
