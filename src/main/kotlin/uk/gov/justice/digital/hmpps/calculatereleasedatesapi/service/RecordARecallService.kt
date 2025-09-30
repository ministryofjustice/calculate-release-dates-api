package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.RECORD_A_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationRequestModel
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode

@Service
class RecordARecallService(
  private val prisonService: PrisonService,
  private val calculationSourceDataService: CalculationSourceDataService,
  private val calculationTransactionalService: CalculationTransactionalService,
  private val calculationReasonRepository: CalculationReasonRepository,
) {

  fun calculateAndValidateForRecordARecall(prisonerId: String): RecordARecallResult {
    val inPrisonSummary = prisonService.getPrisonerInPrisonSummary(prisonerId)
    val bookings = inPrisonSummary.prisonPeriod?.map { it.bookingId to it.bookingSequence }?.distinct()?.sortedByDescending { it.second }?.map { it.first }

    val penultimateBooking = if (bookings != null && bookings.size > 1) {
      bookings[bookings.size - 2]
    } else {
      null
    }

    val inactiveDataOptions = InactiveDataOptions.overrideToIncludeInactiveData()
    val sourceData = calculationSourceDataService.getCalculationSourceData(prisonerId, inactiveDataOptions, listOfNotNull(penultimateBooking))
    val validationResult = calculationTransactionalService.fullValidationFromSourceData(sourceData, CalculationUserInputs())

    if (validationResult.any { criticalValidationErrors.contains(it.code) }) {
      return RecordARecallResult(validationResult)
    }

    val recallReason = calculationReasonRepository.findByDisplayName(RECALL_REASON)
      ?: throw EntityNotFoundException()

    val calculationRequestModel = CalculationRequestModel(
      calculationReasonId = recallReason.id,
      otherReasonDescription = RECALL_REASON,
      calculationUserInputs = null,
    )

    val calculation = calculationTransactionalService.calculate(
      sourceData,
      calculationRequestModel,
      calculationStatus = RECORD_A_RECALL,
    )
    return RecordARecallResult(
      validationResult,
      calculation,
    )
  }

  companion object {
    const val RECALL_REASON = "Requested by record-a-recall service"
    val criticalValidationErrors = listOf(
      ValidationCode.EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT,
      ValidationCode.EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR,
      ValidationCode.EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS,
      ValidationCode.LASPO_AR_SENTENCE_TYPE_INCORRECT,
      ValidationCode.MORE_THAN_ONE_IMPRISONMENT_TERM,
      ValidationCode.MORE_THAN_ONE_LICENCE_TERM,
      ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_RANGE_DATE,
      ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_START_DATE,
      ValidationCode.OFFENCE_MISSING_DATE,
      ValidationCode.SEC236A_SENTENCE_TYPE_INCORRECT,
      ValidationCode.SEC_91_SENTENCE_TYPE_INCORRECT,
      ValidationCode.SENTENCE_HAS_MULTIPLE_TERMS,
      ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM,
      ValidationCode.SENTENCE_HAS_NO_LICENCE_TERM,
      ValidationCode.SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT,
      ValidationCode.SOPC_LICENCE_TERM_NOT_12_MONTHS,
      ValidationCode.ZERO_IMPRISONMENT_TERM,
      ValidationCode.REMAND_ON_OR_AFTER_SENTENCE_DATE,
    )
  }
}
