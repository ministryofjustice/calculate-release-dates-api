package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.RECORD_A_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AutomatedCalculationData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementDirection
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalSentenceId
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallInterimValidationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallSentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallDecision
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallDecisionResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallValidationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallValidationResult.Companion.fromLatestAndPenultimate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceGroup
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Term
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.nomissyncmapping.model.NomisDpsSentenceMapping
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.nomissyncmapping.model.NomisSentenceId
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.remandandsentencing.model.Recall
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.service.ValidationService
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
  private val featureToggles: FeatureToggles,
) {

  fun validate(prisonerId: String): RecordARecallValidationResult {
    val penultimateBookingId = getPenultimateBookingId(prisonerId)
    val sourceData = getSourceData(prisonerId, penultimateBookingId)
    if (penultimateBookingId == null) {
      return RecordARecallValidationResult.fromLatest(validate(sourceData))
    }

    val (latestBookingData, penultimateBookingData) = splitLatestAndPenultimate(sourceData, penultimateBookingId)

    val latestValidationResult = validate(latestBookingData)
    if (latestValidationResult.criticalMessages.isNotEmpty()) {
      return RecordARecallValidationResult.fromLatest(latestValidationResult)
    }
    val penultimateValidationResult = validate(penultimateBookingData)

    return fromLatestAndPenultimate(latestValidationResult, penultimateValidationResult)
  }

  private fun splitLatestAndPenultimate(
    sourceData: CalculationSourceData,
    penultimateBookingId: Long,
  ): Pair<CalculationSourceData, CalculationSourceData> {
    val (penultimateSentenceAndOffences, latestSentenceAndOffences) = sourceData.sentenceAndOffences.partition { it.bookingId == penultimateBookingId }
    val (penultimateFinePayments, latestFinePayments) = sourceData.offenderFinePayments.partition { it.bookingId == penultimateBookingId }

    val latest = sourceData.copy(
      sentenceAndOffences = latestSentenceAndOffences,
      offenderFinePayments = latestFinePayments,
    )

    val penultimate = sourceData.copy(
      sentenceAndOffences = penultimateSentenceAndOffences,
      offenderFinePayments = penultimateFinePayments,
    )

    return latest to penultimate
  }

  private fun getPenultimateBookingId(prisonerId: String): Long? {
    val inPrisonSummary = prisonService.getPrisonerInPrisonSummary(prisonerId)
    // The latest booking has the lowest booking sequence, e.g. booking seq 1 is latest, and seq 2 is penultimate
    val bookings = inPrisonSummary.prisonPeriod?.map { it.bookingId to it.bookingSequence }?.distinct()
      ?.sortedByDescending { it.second }?.map { it.first }

    val penultimateBooking = if (bookings != null && bookings.size > 1) {
      bookings[bookings.size - 2]
    } else {
      null
    }
    return penultimateBooking
  }

  private fun validate(sourceData: CalculationSourceData): RecallInterimValidationResult {
    val validationResult = validationService.validate(sourceData, CalculationUserInputs(), ValidationOrder.INVALID)
    val (criticalValidationMessages, otherValidationMessages) = validationResult.partition { criticalValidationErrors.contains(it.code) }

    return RecallInterimValidationResult(
      criticalMessages = criticalValidationMessages,
      otherMessages = otherValidationMessages,
      earliestSentenceDate = sourceData.sentenceAndOffences.minOf { it.sentenceDate },
    )
  }

  private fun getSourceData(prisonerId: String, penultimateBookingId: Long?): CalculationSourceData {
    val sourceDataLookupOptions = SourceDataLookupOptions.overrideToIncludeInactiveDataAndForceAdjustmentsApi()
    return calculationSourceDataService.getCalculationSourceData(prisonerId, sourceDataLookupOptions, listOfNotNull(penultimateBookingId))
  }

  fun makeRecallDecision(prisonerId: String, recordARecallRequest: RecordARecallRequest): RecordARecallDecisionResult {
    val penultimateBookingId = getPenultimateBookingId(prisonerId)
    val sourceData = getSourceData(prisonerId, penultimateBookingId)

    val latestValidationMessages = if (penultimateBookingId != null) {
      val (latestBookingData, _) = splitLatestAndPenultimate(sourceData, penultimateBookingId)
      validate(latestBookingData)
    } else {
      validate(sourceData)
    }

    if (latestValidationMessages.criticalMessages.isNotEmpty()) {
      return RecordARecallDecisionResult(
        RecordARecallDecision.CRITICAL_ERRORS,
        validationMessages = latestValidationMessages.criticalMessages,
      )
    }

    val validationMessages = validate(sourceData)

    if (validationMessages.otherMessages.isNotEmpty() || validationMessages.criticalMessages.isNotEmpty()) {
      return RecordARecallDecisionResult(
        RecordARecallDecision.VALIDATION,
        validationMessages = validationMessages.otherMessages.plus(validationMessages.criticalMessages),
      )
    }

    val existingPeriodsOfUal = sourceData.bookingAndSentenceAdjustments.adjustmentsApiData!!
      .filter { it.bookingId == sourceData.prisonerDetails.bookingId }
      .filter { it.fromDate != null && it.toDate != null }
      .filterNot { it.recallId != null && recordARecallRequest.recallId != null && it.recallId == recordARecallRequest.recallId }
      .map { it.id to LocalDateRange.ofClosed(it.fromDate, it.toDate) }

    val revPlusOne = recordARecallRequest.revocationDate.plusDays(1)
    val rtcMinusOne = recordARecallRequest.returnToCustodyDate?.minusDays(1)

    val overlappingAdjustments = existingPeriodsOfUal.filter { (_, adjustmentRange) ->
      val overlapsRevocation = adjustmentRange.contains(revPlusOne)
      val overlapsReturnToCustody = rtcMinusOne != null && adjustmentRange.contains(rtcMinusOne)
      val fullyWithinRecallPeriod = rtcMinusOne != null && adjustmentRange.isAfter(revPlusOne) && adjustmentRange.isBefore(rtcMinusOne)

      overlapsRevocation || overlapsReturnToCustody || fullyWithinRecallPeriod
    }

    if (overlappingAdjustments.isNotEmpty()) {
      return RecordARecallDecisionResult(
        decision = RecordARecallDecision.CONFLICTING_ADJUSTMENTS,
        conflictingAdjustments = overlappingAdjustments.map { it.first.toString() },
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

    val output = calculation.calculationOutput!!
    val (sentenceGroupsReleasedBeforeRevocation, sentenceGroupsReleasedAfterRevocation) = output.sentenceGroup.partition { it.to.isBeforeOrEqualTo(recordARecallRequest.revocationDate) }
    val sentencesAndTheirGroups = sentenceGroupsReleasedBeforeRevocation.flatMap { it.sentences.map { sentence -> it to sentence } }
    val (eligibleSentences, ineligibleSentences) = sentencesAndTheirGroups.partition { it.second.sentenceParts().none { part -> part is Term } && it.second.sentenceCalculation.licenceExpiryDate != null }
    val (recallableSentences, expiredSentences) = recallExpiredSentencesOnSameCaseAsRecallableSentences(eligibleSentences, recordARecallRequest.revocationDate)

    if (recallableSentences.isEmpty()) {
      return RecordARecallDecisionResult(
        RecordARecallDecision.NO_RECALLABLE_SENTENCES_FOUND,
      )
    }

    val nomisIds = output.sentences.flatMap { sentence ->
      sentence.sentenceParts().map {
        NomisSentenceId(it.externalSentenceId!!.bookingId, it.externalSentenceId!!.sentenceSequence)
      }
    }
    val mappings = nomisSyncMappingApiClient.postNomisToDpsMappingLookup(nomisIds)

    return RecordARecallDecisionResult(
      RecordARecallDecision.AUTOMATED,
      automatedCalculationData = AutomatedCalculationData(
        calculationRequestId = calculation.calculationRequestId,
        recallableSentences = toRecallableSentences(recallableSentences, mappings),
        expiredSentences = toRecallableSentences(expiredSentences, mappings),
        ineligibleSentences = toRecallableSentences(ineligibleSentences, mappings),
        sentencesBeforeInitialRelease = toRecallableSentences(sentenceGroupsReleasedAfterRevocation.flatMap { it.sentences.map { sentence -> it to sentence } }, mappings),
        unexpectedRecallTypes = findUnexpectedRecallTypes(recallableSentences, booking.externalMovements, recordARecallRequest.revocationDate),
      ),
    )
  }

  private fun recallExpiredSentencesOnSameCaseAsRecallableSentences(
    sentences: List<Pair<SentenceGroup, CalculableSentence>>,
    revocationDate: LocalDate,
  ): Pair<List<Pair<SentenceGroup, CalculableSentence>>, List<Pair<SentenceGroup, CalculableSentence>>> {
    val (recallableSentences, expiredSentences) = sentences.partition { it.second.sentenceCalculation.licenceExpiryDate!!.isAfter(revocationDate) }

    val recallableCases = recallableSentences.flatMap {
      it.second.sentenceParts().map { sentence ->
        NomisCaseId(sentence.externalSentenceId!!.bookingId, sentence.caseSequence!!)
      }
    }

    val expiredSentencesToBeRecalled = expiredSentences.filter {
      it.second.sentenceParts().any { sentence ->
        recallableCases.contains(NomisCaseId(sentence.externalSentenceId!!.bookingId, sentence.caseSequence!!))
      }
    }

    return (recallableSentences + expiredSentencesToBeRecalled) to expiredSentences.filterNot { expiredSentencesToBeRecalled.contains(it) }
  }

  private fun findUnexpectedRecallTypes(
    recallableSentences: List<Pair<SentenceGroup, CalculableSentence>>,
    externalMovements: List<ExternalMovement>,
    revocationDate: LocalDate,
  ): List<Recall.RecallType> {
    val expectedRecallTypes = if (featureToggles.recordARecallFtr56Rules) {
      findExpectedRecallTypesForFtr56(recallableSentences, externalMovements, revocationDate)
    } else {
      findExpectedRecallTypesForFtr(recallableSentences, externalMovements, revocationDate)
    }
    return Recall.RecallType.entries.filterNot { expectedRecallTypes.contains(it) }
  }

  private fun findExpectedRecallTypesForFtr56(
    recallableSentences: List<Pair<SentenceGroup, CalculableSentence>>,
    externalMovements: List<ExternalMovement>,
    revocationDate: LocalDate,
  ): List<Recall.RecallType> {
    val onlyYouthSentences = recallableSentences.all { it.second.sentenceParts().all { sentence -> sentence is StandardDeterminateSentence && sentence.section250 } }
    if (onlyYouthSentences) {
      return findExpectedRecallTypesForFtr(recallableSentences, externalMovements, revocationDate)
    } else {
      val latestReleaseIsHdcAndRevocationBeforeCrd = isLatestReleaseIsHdcAndRevocationBeforeCrd(externalMovements, recallableSentences, revocationDate)
      return if (latestReleaseIsHdcAndRevocationBeforeCrd) {
        listOf(Recall.RecallType.FTR_56, Recall.RecallType.IN_HDC, Recall.RecallType.CUR_HDC)
      } else {
        listOf(Recall.RecallType.FTR_56, Recall.RecallType.LR)
      }
    }
  }

  private fun findExpectedRecallTypesForFtr(
    recallableSentences: List<Pair<SentenceGroup, CalculableSentence>>,
    externalMovements: List<ExternalMovement>,
    revocationDate: LocalDate,
  ): List<Recall.RecallType> {
    val latestReleaseIsHdcAndRevocationBeforeCrd = isLatestReleaseIsHdcAndRevocationBeforeCrd(externalMovements, recallableSentences, revocationDate)
    val any12MonthsOrOver = isAny12MonthsOrOver(recallableSentences)
    return if (latestReleaseIsHdcAndRevocationBeforeCrd) {
      if (any12MonthsOrOver) {
        listOf(Recall.RecallType.FTR_HDC_28, Recall.RecallType.IN_HDC, Recall.RecallType.CUR_HDC)
      } else {
        listOf(Recall.RecallType.FTR_HDC_14, Recall.RecallType.IN_HDC, Recall.RecallType.CUR_HDC)
      }
    } else {
      if (any12MonthsOrOver) {
        listOf(Recall.RecallType.FTR_28, Recall.RecallType.LR)
      } else {
        listOf(Recall.RecallType.FTR_14)
      }
    }
  }
  private fun isLatestReleaseIsHdcAndRevocationBeforeCrd(externalMovements: List<ExternalMovement>, recallableSentences: List<Pair<SentenceGroup, CalculableSentence>>, revocationDate: LocalDate): Boolean {
    val latestReleaseIsHdc = externalMovements.lastOrNull { it.direction == ExternalMovementDirection.OUT }?.movementReason == ExternalMovementReason.HDC
    val latestCrd = recallableSentences.maxOf { it.second.sentenceCalculation.adjustedDeterminateReleaseDate }
    val revocationDateIsBeforeCrd = revocationDate.isBefore(latestCrd)
    return latestReleaseIsHdc && revocationDateIsBeforeCrd
  }

  private fun isAny12MonthsOrOver(recallableSentences: List<Pair<SentenceGroup, CalculableSentence>>) = recallableSentences.any { it.second.durationIsGreaterThanOrEqualTo(12, ChronoUnit.MONTHS) }

  private fun toRecallableSentences(
    sentences: List<Pair<SentenceGroup, CalculableSentence>>,
    mappings: List<NomisDpsSentenceMapping>,
  ): List<RecallableSentence> = sentences.flatMap { (group, sentence) ->
    sentence.sentenceParts().map {
      RecallableSentence(
        sentenceSequence = it.externalSentenceId!!.sentenceSequence,
        bookingId = it.externalSentenceId!!.bookingId,
        uuid = findUuid(it.externalSentenceId!!, mappings),
        sentenceCalculation = RecallSentenceCalculation(
          conditionalReleaseDate = sentence.sentenceCalculation.adjustedDeterminateReleaseDate,
          actualReleaseDate = group.to,
          licenseExpiry = sentence.sentenceCalculation.licenceExpiryDate,
        ),
      )
    }
  }.distinctBy { it.uuid }

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

  private data class NomisCaseId(
    val bookingId: Long,
    val caseSequence: Int,
  )
}
