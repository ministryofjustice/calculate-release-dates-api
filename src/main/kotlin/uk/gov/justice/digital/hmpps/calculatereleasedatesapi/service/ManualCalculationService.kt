package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CouldNotSaveManualEntryException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualCalculationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntryRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceCombinationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceIdentificationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType.MANUAL_ENTRY_JOURNEY_REQUIRED
import java.time.LocalDate
import java.time.Period

@Service
class ManualCalculationService(
  private val prisonService: PrisonService,
  private val bookingService: BookingService,
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationReasonRepository: CalculationReasonRepository,
  private val objectMapper: ObjectMapper,
  private val eventService: EventService,
  private val serviceUserService: ServiceUserService,
  private val nomisCommentService: NomisCommentService,
  private val buildProperties: BuildProperties,
  private val sentenceIdentificationService: SentenceIdentificationService,
  private val sentenceCombinationService: SentenceCombinationService,
  private val validationService: ValidationService,
  private val calculationSourceDataService: CalculationSourceDataService,
  private val calculationService: CalculationService,
) {

  fun hasIndeterminateSentences(bookingId: Long): Boolean {
    val sentencesAndOffences = prisonService.getSentencesAndOffences(bookingId)
    return sentencesAndOffences.any { SentenceCalculationType.isIndeterminate(it.sentenceCalculationType) }
  }

  fun hasRecallSentences(bookingId: Long): Boolean {
    val sentencesAndOffences = prisonService.getSentencesAndOffences(bookingId)
    return sentencesAndOffences.any { SentenceCalculationType.from((it.sentenceCalculationType)).recallType != null }
  }

  @Transactional
  fun storeManualCalculation(
    prisonerId: String,
    manualEntryRequest: ManualEntryRequest,
    isGenuineOverride: Boolean = false,
  ): ManualCalculationResponse {
    val sourceData = calculationSourceDataService.getCalculationSourceData(prisonerId, InactiveDataOptions.default())
    val calculationUserInputs = CalculationUserInputs()
    val booking = bookingService.getBooking(sourceData, calculationUserInputs)

    val effectiveSentenceLength = runCatching {
      calculateEffectiveSentenceLength(booking, manualEntryRequest)
    }.getOrElse {
      log.info("Exception caught calculating ESL for $prisonerId, setting to zero.", it)
      Period.ZERO
    }

    val reasonForCalculation = calculationReasonRepository.findById(manualEntryRequest.reasonForCalculationId).orElse(null)
    val type = chooseManualType(isGenuineOverride, booking.bookingId)
    val request: CalculationRequest = transform(
      booking,
      serviceUserService.getUsername(),
      CalculationStatus.IN_PROGRESS,
      sourceData,
      reasonForCalculation,
      objectMapper,
      manualEntryRequest.otherReasonDescription,
      version = buildProperties.version,
    )
    request.calculationType = type

    try {
      val preMessages = collectPreValidationMessages(sourceData)
      if (preMessages.isNotEmpty()) {
        return finaliseWithValidationErrors(request, preMessages, manualEntryRequest)
      }

      val calculationOutput = calculationService.calculateReleaseDates(booking, calculationUserInputs)
      val postMessages = collectPostValidationMessages(calculationOutput, booking)
      if (postMessages.isNotEmpty()) {
        return finaliseWithValidationErrors(request, postMessages, manualEntryRequest)
      }
      attachReasons(request, emptyList())
      request.calculationStatus = CalculationStatus.CONFIRMED.name
      val savedRequest = calculationRequestRepository.saveAndFlush(request)
      val outcomes = createOutcomes(savedRequest, manualEntryRequest)
      val enteredDates = writeToNomisAndPublishEvent(
        prisonerId = prisonerId,
        booking = booking,
        calculationRequestId = savedRequest.id,
        calculationOutcomes = outcomes,
        isGenuineOverride = isGenuineOverride,
        effectiveSentenceLength = effectiveSentenceLength,
      ) ?: throw CouldNotSaveManualEntryException("There was a problem saving the dates")
      calculationOutcomeRepository.saveAll(outcomes)
      return ManualCalculationResponse(enteredDates, savedRequest.id)
    } catch (ex: Exception) {
      log.error("Error while saving ${request.id}", ex)
      request.calculationStatus = CalculationStatus.ERROR.name
      calculationRequestRepository.saveAndFlush(request)
      throw ex
    }
  }

  @Transactional(readOnly = true)
  fun writeToNomisAndPublishEvent(
    prisonerId: String,
    booking: Booking,
    calculationRequestId: Long,
    calculationOutcomes: List<CalculationOutcome>,
    isGenuineOverride: Boolean,
    effectiveSentenceLength: Period,
  ): Map<ReleaseDateType, LocalDate?>? {
    val calculationRequest = calculationRequestRepository.findById(calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists") }
    val dates = calculationOutcomes.associate { ReleaseDateType.valueOf(it.calculationDateType) to it.outcomeDate }
    val updateOffenderDates = UpdateOffenderDates(
      calculationUuid = calculationRequest.calculationReference,
      submissionUser = serviceUserService.getUsername(),
      keyDates = transform(dates, effectiveSentenceLength),
      noDates = dates.containsKey(ReleaseDateType.None),
      reason = calculationRequest.reasonForCalculation?.nomisReason,
      comment = nomisCommentService.getManualNomisComment(calculationRequest, dates, isGenuineOverride),
    )
    try {
      prisonService.postReleaseDates(booking.bookingId, updateOffenderDates)
    } catch (ex: Exception) {
      CalculationTransactionalService.log.error("Nomis write failed: ${ex.message}")
      throw EntityNotFoundException(
        "Writing release dates to NOMIS failed for prisonerId $prisonerId " +
          "and bookingId ${booking.bookingId}",
      )
    }
    runCatching {
      eventService.publishReleaseDatesChangedEvent(prisonerId, booking.bookingId)
    }.onFailure { error ->
      log.error(
        "Failed to send release-dates-changed-event for prisoner ID $prisonerId",
        error,
      )
    }
    return dates
  }

  fun calculateEffectiveSentenceLength(booking: Booking, manualEntryRequest: ManualEntryRequest): Period {
    val hasFixedTermRecallSentences = booking.sentences.filter { it.recallType == RecallType.FIXED_TERM_RECALL_14 || it.recallType == RecallType.FIXED_TERM_RECALL_28 }
    if (hasIndeterminateSentences(booking.bookingId) ||
      (hasFixedTermRecallSentences.isNotEmpty() && booking.returnToCustodyDate == null)
    ) {
      return Period.ZERO
    } else {
      booking.sentences.forEach {
        sentenceIdentificationService.identify(it, booking.offender)
      }
      val sentences = sentenceCombinationService.getSentencesToCalculate(booking.sentences, booking.offender)
      val earliestSentenceDate = sentences.minOfOrNull { it.sentencedAt }
      val sed = getSED(manualEntryRequest)
      return if (sed != null && earliestSentenceDate != null) {
        val period = Period.between(earliestSentenceDate, sed)
        if (!period.isNegative) period else Period.ZERO
      } else {
        Period.ZERO
      }
    }
  }

  /**
   * Compare current booking details in Nomis against the previous calculation.
   * Previous calculation must be present and manually calculated.
   * Manual calculations with no submitted dates are ignored.
   * Both calculations must be identical to return a positive match.
   */
  fun equivalentManualCalculationExists(prisonerId: String): Boolean {
    val sourceData = calculationSourceDataService.getCalculationSourceData(prisonerId, InactiveDataOptions.default())
    val calculationUserInputs = CalculationUserInputs()
    val currentBooking = bookingService.getBooking(sourceData, calculationUserInputs)
    val previousCalculation = calculationRequestRepository
      .findLatestManualCalculation(prisonerId, CalculationStatus.CONFIRMED.name) ?: return false

    if (
      previousCalculation.calculationOutcomes.size == 1 &&
      previousCalculation.calculationOutcomes.first().calculationDateType == "None"
    ) {
      return false
    }

    val previousJson = runCatching {
      objectToJson(currentBooking, objectMapper)
    }.getOrElse { return false }

    return previousJson == previousCalculation.inputData
  }

  fun getSED(manualEntryRequest: ManualEntryRequest): LocalDate? {
    val sed = manualEntryRequest.selectedManualEntryDates.find { it.dateType == ReleaseDateType.SED }
    return sed?.date?.toLocalDate()
  }

  private fun chooseManualType(isGenuineOverride: Boolean, bookingId: Long): CalculationType = when {
    isGenuineOverride -> CalculationType.MANUAL_OVERRIDE
    hasIndeterminateSentences(bookingId) -> CalculationType.MANUAL_INDETERMINATE
    else -> CalculationType.MANUAL_DETERMINATE
  }

  private fun collectPreValidationMessages(
    sourceData: CalculationSourceData,
  ): List<ValidationMessage> {
    val pre = validationService.validateSupportedSentencesAndCalculations(sourceData)
    return buildList {
      addAll(pre.unsupportedCalculationMessages)
      addAll(pre.unsupportedSentenceMessages)
    }
  }

  private fun collectPostValidationMessages(
    calculationOutput: CalculationOutput,
    booking: Booking,
  ): List<ValidationMessage> = validationService
    .validateBookingAfterCalculation(calculationOutput, booking)
    .filter { it.type == MANUAL_ENTRY_JOURNEY_REQUIRED }

  private fun finaliseWithValidationErrors(
    request: CalculationRequest,
    messages: List<ValidationMessage>,
    manualEntryRequest: ManualEntryRequest,
  ): ManualCalculationResponse {
    attachReasons(request, messages)

    request.calculationStatus = CalculationStatus.CONFIRMED.name
    val savedRequest = calculationRequestRepository.saveAndFlush(request)
    val outcomes = createOutcomes(savedRequest, manualEntryRequest)
    calculationOutcomeRepository.saveAll(outcomes)
    return ManualCalculationResponse(emptyMap(), savedRequest.id)
  }

  private fun createOutcomes(
    request: CalculationRequest,
    manualEntryRequest: ManualEntryRequest,
  ): List<CalculationOutcome> = manualEntryRequest.selectedManualEntryDates.map { transform(request, it) }

  private fun attachReasons(
    request: CalculationRequest,
    messages: List<ValidationMessage>,
  ) {
    request.manualCalculationReason = messages.map { transform(request, it) }
  }

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
