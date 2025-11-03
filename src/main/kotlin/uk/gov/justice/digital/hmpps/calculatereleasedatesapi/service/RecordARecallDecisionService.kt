package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.RECORD_A_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalSentenceId
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallSentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallDecision
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallDecisionResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallValidationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Term
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.nomissyncmapping.model.NomisDpsSentenceMapping
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.nomissyncmapping.model.NomisSentenceId
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.remandandsentencing.model.Recall
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.RecordARecallService.Companion.RECALL_REASON
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.service.ValidationService
import java.util.UUID

@Service
class RecordARecallDecisionService(
  private val prisonService: PrisonService,
  private val calculationSourceDataService: CalculationSourceDataService,
  private val calculationTransactionalService: CalculationTransactionalService,
  private val calculationReasonRepository: CalculationReasonRepository,
  private val validationService: ValidationService,
  private val bookingService: BookingService,
  private val nomisSyncMappingApiClient: NomisSyncMappingApiClient,
) {

  fun validate(prisonerId: String): RecordARecallValidationResult {
    val sourceData = getSourceData(prisonerId)
    return validate(sourceData)
  }

  private fun validate(sourceData: CalculationSourceData): RecordARecallValidationResult {
    val validationResult = validationService.validate(sourceData, CalculationUserInputs(), ValidationOrder.INVALID)
    val (criticalValidationMessages, otherValidationMessages) = validationResult.partition { criticalValidationErrors.contains(it.code) }

    return RecordARecallValidationResult(
      criticalValidationMessages = criticalValidationMessages,
      otherValidationMessages = otherValidationMessages,
      earliestSentenceDate = sourceData.sentenceAndOffences.minOf { it.sentenceDate },
    )
  }

  private fun getSourceData(prisonerId: String): CalculationSourceData {
    val inPrisonSummary = prisonService.getPrisonerInPrisonSummary(prisonerId)
    val bookings = inPrisonSummary.prisonPeriod?.map { it.bookingId to it.bookingSequence }?.distinct()?.sortedByDescending { it.second }?.map { it.first }

    val penultimateBooking = if (bookings != null && bookings.size > 1) {
      bookings[bookings.size - 2]
    } else {
      null
    }

    val inactiveDataOptions = InactiveDataOptions.overrideToIncludeInactiveData()
    return calculationSourceDataService.getCalculationSourceData(prisonerId, inactiveDataOptions, listOfNotNull(penultimateBooking))
  }

  fun makeRecallDecision(prisonerId: String, recordARecallRequest: RecordARecallRequest): RecordARecallDecisionResult {
    val sourceData = getSourceData(prisonerId)
    val validationMessages = validate(sourceData)

    if (validationMessages.criticalValidationMessages.isNotEmpty()) {
      return RecordARecallDecisionResult(
        RecordARecallDecision.CRITICAL_ERRORS,
        validationMessages = validationMessages.criticalValidationMessages,
      )
    }
    if (validationMessages.otherValidationMessages.isNotEmpty()) {
      return RecordARecallDecisionResult(
        RecordARecallDecision.VALIDATION,
        validationMessages = validationMessages.otherValidationMessages,
      )
    }

    val recallReason = calculationReasonRepository.findByDisplayName(RECALL_REASON)
      ?: throw EntityNotFoundException()

    val booking = bookingService.getBooking(sourceData)
    val calculation = calculationTransactionalService.calculate(
      booking = booking,
      calculationStatus = RECORD_A_RECALL,
      sourceData = sourceData,
      reasonForCalculation = recallReason,
      calculationUserInputs = CalculationUserInputs(),
    )

    val existingPeriodsOfUal = booking.adjustments.getOrEmptyList(AdjustmentType.UNLAWFULLY_AT_LARGE)
      .filter { it.fromDate != null && it.toDate != null }
      .map { LocalDateRange.of(it.fromDate, it.toDate) }
    val anyOverlappingAdjustments = existingPeriodsOfUal.any { it.contains(recordARecallRequest.revocationDate) }
    if (anyOverlappingAdjustments) {
      return RecordARecallDecisionResult(
        decision = RecordARecallDecision.CONFLICTING_ADJUSTMENTS,
      )
    }

    val output = calculation.calculationOutput!!
    val sentenceGroupsReleasedBeforeRevocation = output.sentenceGroup.filter { it.to.isBeforeOrEqualTo(recordARecallRequest.revocationDate) }
    val recallableSentenceGroups = sentenceGroupsReleasedBeforeRevocation.flatMap { it.sentences.map { sentence -> it to sentence } }
      .filter { it.second.sentenceCalculation.licenceExpiryDate?.isAfter(recordARecallRequest.revocationDate) == true }
      .filter { it.second.sentenceParts().none { part -> part is Term } }

    if (recallableSentenceGroups.isEmpty()) {
      return RecordARecallDecisionResult(
        RecordARecallDecision.NO_RECALLABLE_SENTENCES_FOUND,
      )
    }

    val nomisIds = recallableSentenceGroups.flatMap { (group, sentence) ->
      sentence.sentenceParts().map {
        NomisSentenceId(it.externalSentenceId!!.bookingId, it.externalSentenceId!!.sentenceSequence)
      }
    }
    val mappings = nomisSyncMappingApiClient.postNomisToDpsMappingLookup(nomisIds)

    val anyNonSdsSentences = recallableSentenceGroups.any { (group, sentence) -> sentence.sentenceParts().any { it !is StandardDeterminateSentence } }

    return RecordARecallDecisionResult(
      RecordARecallDecision.AUTOMATED,
      recallableSentences = recallableSentenceGroups.flatMap { (group, sentence) ->
        sentence.sentenceParts().map {
          RecallableSentence(
            sentenceSequence = it.externalSentenceId!!.sentenceSequence,
            bookingId = it.externalSentenceId!!.bookingId,
            uuid = findUuid(it.externalSentenceId!!, mappings),
            sentenceCalculation = RecallSentenceCalculation(
              conditionalReleaseDate = sentence.sentenceCalculation.adjustedDeterminateReleaseDate,
              actualReleaseDate = group.to,
              licenseExpiry = sentence.sentenceCalculation.licenceExpiryDate!!,
            ),
          )
        }
      },
      eligibleRecallTypes = if (anyNonSdsSentences) listOf(Recall.RecallType.LR) else Recall.RecallType.entries,
      calculationRequestId = calculation.calculationRequestId,
    )
  }

  private fun findUuid(
    externalSentenceId: ExternalSentenceId,
    mappings: List<NomisDpsSentenceMapping>,
  ): UUID {
    val mapping = mappings.find { it.nomisSentenceId == NomisSentenceId(externalSentenceId.bookingId, externalSentenceId.sentenceSequence) }
    if (mapping == null) {
      throw IllegalArgumentException("No DPS mapping found for $externalSentenceId")
    }
    return UUID.fromString(mapping.dpsSentenceId)
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
