package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDatesSubmission
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.TrancheOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.ERROR
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.BreakdownChangedSinceLastCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CalculationDataHasChangedError
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CalculationNotFoundException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PreconditionFailedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PrisonApiDataNotFoundException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationRequestModel
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntrySelectedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmitCalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.TrancheOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import java.util.UUID

@Service
@Transactional(readOnly = true)
class CalculationTransactionalService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
  private val calculationReasonRepository: CalculationReasonRepository,
  private val objectMapper: ObjectMapper,
  private val prisonService: PrisonService,
  private val prisonApiDataMapper: PrisonApiDataMapper,
  private val calculationService: CalculationService,
  private val bookingService: BookingService,
  private val validationService: ValidationService,
  private val eventService: EventService,
  private val serviceUserService: ServiceUserService,
  private val approvedDatesSubmissionRepository: ApprovedDatesSubmissionRepository,
  private val nomisCommentService: NomisCommentService,
  private val buildProperties: BuildProperties,
  private val trancheOutcomeRepository: TrancheOutcomeRepository,
) {

  /*
   * There are 4 stages of full validation:
   * 1. validate user input data, the raw sentence and offence data from NOMIS
   * 2. validate the raw Booking (NOMIS offence data transformed into a Booking object)
   * 3. Run the calculation and catch any errors thrown by the calculation algorithm
   * 4. Validate the post calculation Booking (The Booking is transformed during the calculation). e.g. Consecutive sentences (aggregates)
   *
   * activeDataOnly is only used by the test 1000 calcs functionality
   */
  fun fullValidation(
    prisonerId: String,
    calculationUserInputs: CalculationUserInputs,
    inactiveDataOptions: InactiveDataOptions = InactiveDataOptions.default(),
  ): List<ValidationMessage> {
    log.info("Full Validation for $prisonerId")
    val sourceData = prisonService.getPrisonApiSourceData(prisonerId, inactiveDataOptions)
    return fullValidationFromSourceData(sourceData, calculationUserInputs)
  }

  fun fullValidationFromSourceData(sourceData: PrisonApiSourceData, calculationUserInputs: CalculationUserInputs): List<ValidationMessage> {
    val initialValidationMessages = validationService.validateBeforeCalculation(sourceData, calculationUserInputs)

    if (initialValidationMessages.isNotEmpty()) {
      log.info(initialValidationMessages.joinToString("\n"))
      return initialValidationMessages
    }

    val booking = bookingService.getBooking(sourceData, calculationUserInputs)

    return fullValidationFromBookingData(booking, calculationUserInputs)
  }

  fun fullValidationFromBookingData(booking: Booking, calculationUserInputs: CalculationUserInputs): List<ValidationMessage> {
    val bookingValidationMessages = validationService.validateBeforeCalculation(booking)

    if (bookingValidationMessages.isNotEmpty()) {
      return bookingValidationMessages
    }

    val calculationOutput = calculationService.calculateReleaseDates(
      booking,
      calculationUserInputs,
    )

    return validationService.validateBookingAfterCalculation(calculationOutput, booking)
  }

  @Transactional
  fun validateAndCalculate(
    prisonerId: String,
    calculationUserInputs: CalculationUserInputs,
    calculationReason: CalculationReason,
    providedSourceData: PrisonApiSourceData,
    calculationType: CalculationStatus = PRELIMINARY,
  ): ValidationResult {
    var messages =
      validationService.validateBeforeCalculation(providedSourceData, calculationUserInputs) // Validation stage 1 of 3
    if (messages.isNotEmpty()) return ValidationResult(messages, null, null, null)
    // getBooking relies on the previous validation stage to have succeeded
    val booking = bookingService.getBooking(providedSourceData, calculationUserInputs)
    messages = validationService.validateBeforeCalculation(booking) // Validation stage 2 of 4
    if (messages.isNotEmpty()) return ValidationResult(messages, null, null, null)
    val calculationOutput = calculationService.calculateReleaseDates(booking, calculationUserInputs) // Validation stage 3 of 4
    val calculationResult = calculationOutput.calculationResult
    messages = validationService.validateBookingAfterCalculation(calculationOutput, booking) // Validation stage 4 of 4

    val calculatedReleaseDates = calculate(
      booking,
      calculationType,
      providedSourceData,
      calculationReason,
      calculationUserInputs,
      historicalTusedSource = providedSourceData.historicalTusedData?.historicalTusedSource,
    )
    return ValidationResult(messages, booking, calculatedReleaseDates, calculationResult)
  }

  fun supportedValidation(prisonerId: String, inactiveDataOptions: InactiveDataOptions = InactiveDataOptions.default()): List<ValidationMessage> {
    val sourceData = prisonService.getPrisonApiSourceData(prisonerId, inactiveDataOptions)
    return validationService.validateSupportedSentencesAndCalculations(sourceData)
  }

  @Transactional
  fun calculate(
    prisonerId: String,
    calculationRequestModel: CalculationRequestModel,
    inactiveDataOptions: InactiveDataOptions = InactiveDataOptions.default(),
    calculationType: CalculationStatus = PRELIMINARY,
  ): CalculatedReleaseDates {
    val sourceData = prisonService.getPrisonApiSourceData(prisonerId, inactiveDataOptions)
    val calculationUserInputs = calculationRequestModel.calculationUserInputs ?: CalculationUserInputs()
    val booking = bookingService.getBooking(sourceData, calculationUserInputs)
    val reasonForCalculation = calculationReasonRepository.findById(calculationRequestModel.calculationReasonId)
      .orElse(null) // TODO: This should thrown an EntityNotFoundException when the reason is mandatory.
    try {
      return calculate(
        booking,
        calculationType,
        sourceData,
        reasonForCalculation,
        calculationUserInputs,
        calculationRequestModel.otherReasonDescription,
        historicalTusedSource = sourceData.historicalTusedData?.historicalTusedSource,
      )
    } catch (error: Exception) {
      recordError(
        booking,
        sourceData,
        calculationUserInputs,
        error,
        reasonForCalculation,
        calculationRequestModel.otherReasonDescription,
      )
      throw error
    }
  }

  @Transactional
  fun validateAndConfirmCalculation(
    calculationRequestId: Long,
    submitCalculationRequest: SubmitCalculationRequest,
  ): CalculatedReleaseDates {
    val calculationRequest =
      calculationRequestRepository.findByIdAndCalculationStatus(
        calculationRequestId,
        PRELIMINARY.name,
      ).orElseThrow {
        EntityNotFoundException("No preliminary calculation exists for calculationRequestId $calculationRequestId")
      }
    val sourceData = prisonService.getPrisonApiSourceData(calculationRequest.prisonerId, InactiveDataOptions.default())
    val userInput = transform(calculationRequest.calculationRequestUserInput)
    val booking = bookingService.getBooking(sourceData, userInput)

    if (calculationRequest.inputData.hashCode() != objectToJson(booking, objectMapper).hashCode()) {
      throw PreconditionFailedException("The booking data used for the preliminary calculation has changed")
    }

    if (validationService.validateBeforeCalculation(sourceData, userInput).isNotEmpty()) {
      throw PreconditionFailedException("The booking now fails validation")
    }

    return confirmCalculation(
      calculationRequest.prisonerId,
      submitCalculationRequest.calculationFragments, sourceData, booking, userInput,
      submitCalculationRequest.approvedDates, submitCalculationRequest.isSpecialistSupport,
      calculationRequest.reasonForCalculation,
      calculationRequest.otherReasonForCalculation,
      sourceData.historicalTusedData?.historicalTusedSource,
    )
  }

  private fun confirmCalculation(
    prisonerId: String,
    calculationFragments: CalculationFragments,
    sourceData: PrisonApiSourceData,
    booking: Booking,
    userInput: CalculationUserInputs,
    approvedDates: List<ManualEntrySelectedDate>?,
    isSpecialistSupport: Boolean? = false,
    reasonForCalculation: CalculationReason?,
    otherReasonForCalculation: String?,
    historicalTusedSource: HistoricalTusedSource? = null,
  ): CalculatedReleaseDates {
    try {
      val calculationType = if (approvedDates != null) {
        CalculationType.CALCULATED
      } else if (isSpecialistSupport!!) {
        CalculationType.CALCULATED_BY_SPECIALIST_SUPPORT
      } else {
        CalculationType.CALCULATED_WITH_APPROVED_DATES
      }
      val calculation = calculate(
        booking,
        CONFIRMED,
        sourceData,
        reasonForCalculation,
        userInput,
        otherReasonForCalculation,
        calculationFragments,
        calculationType,
        historicalTusedSource,
      )
      if (!approvedDates.isNullOrEmpty()) {
        storeApprovedDates(calculation, approvedDates)
      }
      writeToNomisAndPublishEvent(prisonerId, booking, calculation, approvedDates, isSpecialistSupport)
      return calculation
    } catch (error: Exception) {
      recordError(booking, sourceData, userInput, error, reasonForCalculation, otherReasonForCalculation)
      throw error
    }
  }

  @Transactional
  fun calculate(
    booking: Booking,
    calculationStatus: CalculationStatus,
    sourceData: PrisonApiSourceData,
    reasonForCalculation: CalculationReason?,
    calculationUserInputs: CalculationUserInputs,
    otherCalculationReason: String? = null,
    calculationFragments: CalculationFragments? = null,
    calculationType: CalculationType = CalculationType.CALCULATED,
    historicalTusedSource: HistoricalTusedSource? = null,
  ): CalculatedReleaseDates {
    val calculationRequest =
      calculationRequestRepository.save(
        transform(
          booking,
          serviceUserService.getUsername(),
          calculationStatus,
          sourceData,
          reasonForCalculation,
          objectMapper,
          otherCalculationReason,
          calculationUserInputs,
          calculationFragments,
          calculationType,
          historicalTusedSource,
          buildProperties.version,
        ),
      )
    val calculationOutput = calculationService.calculateReleaseDates(booking, calculationUserInputs)
    val calculationResult = calculationOutput.calculationResult
    calculationResult.dates.forEach {
      calculationOutcomeRepository.save(transform(calculationRequest, it.key, it.value))
    }
    trancheOutcomeRepository.save(
      TrancheOutcome(
        calculationRequest = calculationRequest,
        allocatedTranche = calculationResult.sdsEarlyReleaseAllocatedTranche,
        tranche = calculationResult.sdsEarlyReleaseTranche,
        affectedBySds40 = calculationResult.affectedBySds40,
      ),
    )

    return CalculatedReleaseDates(
      dates = calculationResult.dates,
      effectiveSentenceLength = calculationResult.effectiveSentenceLength,
      prisonerId = sourceData.prisonerDetails.offenderNo,
      bookingId = sourceData.prisonerDetails.bookingId,
      calculationFragments = calculationFragments,
      calculationRequestId = calculationRequest.id,
      calculationStatus = calculationStatus,
      approvedDates = null,
      calculationReference = calculationRequest.calculationReference,
      calculationReason = reasonForCalculation,
      otherReasonDescription = otherCalculationReason,
      calculationDate = calculationRequest.calculatedAt.toLocalDate(),
      historicalTusedSource = calculationResult.historicalTusedSource,
      sdsEarlyReleaseAllocatedTranche = calculationResult.sdsEarlyReleaseAllocatedTranche,
      sdsEarlyReleaseTranche = calculationResult.sdsEarlyReleaseTranche,
      calculationOutput = calculationOutput,
    )
  }

  @Transactional(readOnly = true)
  fun calculateWithBreakdown(
    booking: Booking,
    previousCalculationResults: CalculatedReleaseDates,
    calculationUserInputs: CalculationUserInputs,
  ): CalculationBreakdown {
    if (previousCalculationResults.calculationType == CalculationType.CALCULATED) {
      val calculationOutput = calculationService.calculateReleaseDates(booking, calculationUserInputs)
      val calculationResult = calculationOutput.calculationResult
      if (calculationResult.dates == previousCalculationResults.dates) {
        return transform(
          calculationOutput,
          calculationResult.breakdownByReleaseDateType,
          calculationResult.otherDates,
          calculationResult.ersedNotApplicableDueToDtoLaterThanCrd,
        )
      } else {
        throw BreakdownChangedSinceLastCalculation("Calculation no longer agrees with algorithm.")
      }
    }
    return CalculationBreakdown(emptyList(), null, emptyMap(), emptyMap())
  }

  @Transactional(readOnly = true)
  fun findConfirmedCalculationResults(prisonerId: String, bookingId: Long): CalculatedReleaseDates {
    val calculationRequest =
      calculationRequestRepository.findFirstByPrisonerIdAndBookingIdAndCalculationStatusOrderByCalculatedAtDesc(
        prisonerId,
        bookingId,
        CONFIRMED.name,
      ).orElseThrow {
        EntityNotFoundException("No confirmed calculation exists for prisoner $prisonerId and bookingId $bookingId")
      }

    return transform(calculationRequest)
  }

  @Transactional(readOnly = true)
  fun findCalculationResults(calculationRequestId: Long): CalculatedReleaseDates = transform(getCalculationRequest(calculationRequestId))

  @Transactional(readOnly = true)
  fun findUserInput(calculationRequestId: Long): CalculationUserInputs {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    return transform(calculationRequest.calculationRequestUserInput)
  }

  @Transactional(readOnly = true)
  fun findSentenceAndOffencesFromCalculation(calculationRequestId: Long): List<SentenceAndOffenceWithReleaseArrangements> {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    if (calculationRequest.sentenceAndOffences == null) {
      throw PrisonApiDataNotFoundException("Sentences and offence data not found for calculation $calculationRequestId")
    }
    return prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)
  }

  @Transactional(readOnly = true)
  fun findPrisonerDetailsFromCalculation(calculationRequestId: Long): PrisonerDetails {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    if (calculationRequest.prisonerDetails == null) {
      throw PrisonApiDataNotFoundException("Prisoner details data not found for calculation $calculationRequestId")
    }
    return prisonApiDataMapper.mapPrisonerDetails(calculationRequest)
  }

  @Transactional(readOnly = true)
  fun findBookingAndSentenceAdjustmentsFromCalculation(calculationRequestId: Long): BookingAndSentenceAdjustments {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    if (calculationRequest.adjustments == null) {
      throw PrisonApiDataNotFoundException("Adjustments data not found for calculation $calculationRequestId")
    }
    return prisonApiDataMapper.mapBookingAndSentenceAdjustments(calculationRequest)
  }

  @Transactional(readOnly = true)
  fun findReturnToCustodyDateFromCalculation(calculationRequestId: Long): ReturnToCustodyDate? {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    if (calculationRequest.returnToCustodyDate == null) {
      return null
    }
    return prisonApiDataMapper.mapReturnToCustodyDate(calculationRequest)
  }

  private fun getCalculationRequest(calculationRequestId: Long): CalculationRequest = calculationRequestRepository.findById(calculationRequestId).orElseThrow {
    EntityNotFoundException("No calculation results exist for calculationRequestId $calculationRequestId ")
  }

  private fun getCalculationRequestByReference(calculationReference: String): CalculationRequest = calculationRequestRepository.findByCalculationReference(UUID.fromString(calculationReference)).orElseThrow {
    EntityNotFoundException("No calculation results exist for calculationReference $calculationReference ")
  }

  @Transactional(readOnly = true)
  fun writeToNomisAndPublishEvent(
    prisonerId: String,
    booking: Booking,
    calculation: CalculatedReleaseDates,
    approvedDates: List<ManualEntrySelectedDate>?,
    isSpecialistSupport: Boolean? = false,
  ) {
    val calculationRequest = calculationRequestRepository.findById(calculation.calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists") }

    val updateOffenderDates = UpdateOffenderDates(
      calculationUuid = calculationRequest.calculationReference,
      submissionUser = serviceUserService.getUsername(),
      keyDates = transform(calculation, approvedDates),
      noDates = false,
      reason = calculationRequest.reasonForCalculation?.nomisReason,
      comment = nomisCommentService.getNomisComment(calculationRequest, isSpecialistSupport!!, approvedDates),
    )
    try {
      prisonService.postReleaseDates(booking.bookingId, updateOffenderDates)
    } catch (ex: Exception) {
      log.error("Nomis write failed: ${ex.message}")
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
  }

  @Transactional
  fun recordError(
    booking: Booking,
    sourceData: PrisonApiSourceData,
    calculationUserInputs: CalculationUserInputs?,
    error: Exception,
    reasonForCalculation: CalculationReason?,
    otherReasonForCalculation: String?,
  ) {
    calculationRequestRepository.save(
      transform(
        booking,
        serviceUserService.getUsername(),
        ERROR,
        sourceData,
        reasonForCalculation,
        objectMapper,
        otherReasonForCalculation,
        calculationUserInputs,
        version = buildProperties.version,
      ),
    )
  }

  @Transactional
  fun storeApprovedDates(calculation: CalculatedReleaseDates, approvedDates: List<ManualEntrySelectedDate>) {
    val foundCalculation = calculationRequestRepository.findById(calculation.calculationRequestId)
    foundCalculation.map {
      val submittedDatesToSave = approvedDates.map { approvedDate ->
        ApprovedDates(
          calculationDateType = approvedDate.dateType.name,
          outcomeDate = approvedDate.date!!.toLocalDate(),
        )
      }
      val approvedDatesSubmission = ApprovedDatesSubmission(
        calculationRequest = it,
        bookingId = it.bookingId,
        prisonerId = it.prisonerId,
        submittedByUsername = it.calculatedByUsername,
        approvedDates = submittedDatesToSave,
      )
      approvedDatesSubmissionRepository.save(approvedDatesSubmission)
    }
      .orElseThrow { CalculationNotFoundException("Could not find calculation with request id: ${calculation.calculationRequestId}") }
  }

  fun findCalculationResultsByCalculationReference(
    calculationReference: String,
    checkForChange: Boolean = false,
  ): CalculatedReleaseDates {
    val calculationRequest = getCalculationRequestByReference(calculationReference)

    return if (checkForChange) {
      log.info("Checking for change in data")
      val sourceData = prisonService.getPrisonApiSourceData(calculationRequest.prisonerId, InactiveDataOptions.default())
      val originalCalculationAdjustments =
        objectMapper.treeToValue(calculationRequest.adjustments, BookingAndSentenceAdjustments::class.java)
      val originalPrisonerDetails =
        objectMapper.treeToValue(calculationRequest.prisonerDetails, PrisonerDetails::class.java)
      val originalSentenceAndOffences = calculationRequest.sentenceAndOffences?.let {
        prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)
      }
      val originalReturnToCustodyDate =
        objectMapper.treeToValue(calculationRequest.returnToCustodyDate, ReturnToCustodyDate::class.java)
      val bookingAndSentenceAdjustments = sourceData.bookingAndSentenceAdjustments
      val prisonerDetails = sourceData.prisonerDetails
      val sentenceAndOffences = sourceData.sentenceAndOffences
      val returnToCustodyDate = sourceData.returnToCustodyDate
      if (originalCalculationAdjustments == bookingAndSentenceAdjustments && prisonerDetails == originalPrisonerDetails && sentenceAndOffences == originalSentenceAndOffences && returnToCustodyDate == originalReturnToCustodyDate) {
        return transform(calculationRequest)
      } else {
        throw CalculationDataHasChangedError(calculationReference, calculationRequest.prisonerId)
      }
    } else {
      transform(calculationRequest)
    }
  }

  fun validateForManualBooking(prisonerId: String): List<ValidationMessage> {
    val sourceData = prisonService.getPrisonApiSourceData(prisonerId, InactiveDataOptions.default())
    return validationService.validateSentenceForManualEntry(sourceData.sentenceAndOffences)
  }

  fun validateRequestedDates(dates: List<String>): List<ValidationMessage> = validationService.validateRequestedDates(dates)

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
