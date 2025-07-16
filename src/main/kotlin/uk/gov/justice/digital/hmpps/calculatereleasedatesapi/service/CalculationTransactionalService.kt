package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.info.BuildProperties
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcomeHistoricOverride
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.TrancheOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.ERROR
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.BreakdownChangedSinceLastCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CalculationDataHasChangedError
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PreconditionFailedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PrisonApiDataNotFoundException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationRequestModel
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntrySelectedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmitCalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SupportedValidationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeHistoricOverrideRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.TrancheOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import java.time.LocalDate
import java.util.UUID

@Service
class CalculationTransactionalService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
  private val calculationReasonRepository: CalculationReasonRepository,
  private val calculationOutcomeHistoricOverrideRepository: CalculationOutcomeHistoricOverrideRepository,
  private val objectMapper: ObjectMapper,
  private val calculationSourceDataService: CalculationSourceDataService,
  private val sourceDataMapper: SourceDataMapper,
  private val calculationService: CalculationService,
  private val bookingService: BookingService,
  private val validationService: ValidationService,
  private val serviceUserService: ServiceUserService,
  private val calculationConfirmationService: CalculationConfirmationService,
  private val dominantHistoricDateService: DominantHistoricDateService,
  private val buildProperties: BuildProperties,
  private val trancheOutcomeRepository: TrancheOutcomeRepository,
  private val featureToggles: FeatureToggles,
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
  @Transactional(readOnly = true)
  fun fullValidation(
    prisonerId: String,
    calculationUserInputs: CalculationUserInputs,
    inactiveDataOptions: InactiveDataOptions = InactiveDataOptions.default(),
  ): List<ValidationMessage> {
    log.info("Full Validation for $prisonerId")
    val sourceData = calculationSourceDataService.getCalculationSourceData(prisonerId, inactiveDataOptions)
    return fullValidationFromSourceData(sourceData, calculationUserInputs)
  }

  @Transactional(readOnly = true)
  fun fullValidationFromSourceData(sourceData: CalculationSourceData, calculationUserInputs: CalculationUserInputs): List<ValidationMessage> {
    val initialValidationMessages = validationService.validateBeforeCalculation(sourceData, calculationUserInputs)

    if (initialValidationMessages.isNotEmpty()) {
      log.info(initialValidationMessages.joinToString("\n"))
      return initialValidationMessages
    }

    val booking = bookingService.getBooking(sourceData, calculationUserInputs)

    return fullValidationFromBookingData(booking, calculationUserInputs)
  }

  @Transactional(readOnly = true)
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

  // Deliberately not transactional. Transaction started in bulk services.
  fun validateAndCalculateForBulk(
    prisonerId: String,
    calculationUserInputs: CalculationUserInputs,
    calculationReason: CalculationReason,
    providedSourceData: CalculationSourceData,
    calculationType: CalculationStatus = PRELIMINARY,
    usernameOverride: String? = null,
  ): ValidationResult {
    var messages =
      validationService.validateBeforeCalculation(providedSourceData, calculationUserInputs, bulkCalcValidation = true) // Validation stage 1 of 3
    if (messages.isNotEmpty()) return ValidationResult(messages, null, null, null)
    // getBooking relies on the previous validation stage to have succeeded
    val booking = bookingService.getBooking(providedSourceData, calculationUserInputs)
    messages = validationService.validateBeforeCalculation(booking) // Validation stage 2 of 4
    if (messages.isNotEmpty()) return ValidationResult(messages, null, null, null)
    val calculationOutput = calculationService.calculateReleaseDates(booking, calculationUserInputs) // Validation stage 3 of 4
    val calculationResult = calculationOutput.calculationResult
    messages = validationService.validateBookingAfterCalculation(calculationOutput, booking) // Validation stage 4 of 4
    if (messages.isNotEmpty()) return ValidationResult(messages, null, null, null)

    val calculatedReleaseDates = calculate(
      booking,
      calculationType,
      providedSourceData,
      calculationReason,
      calculationUserInputs,
      historicalTusedSource = providedSourceData.historicalTusedData?.historicalTusedSource,
      usernameOverride = usernameOverride,
    )
    return ValidationResult(messages, booking, calculatedReleaseDates, calculationResult)
  }

  @Transactional(readOnly = true)
  fun supportedValidation(prisonerId: String, inactiveDataOptions: InactiveDataOptions = InactiveDataOptions.default()): SupportedValidationResponse {
    val sourceData = calculationSourceDataService.getCalculationSourceData(prisonerId, inactiveDataOptions)
    val noInputs = CalculationUserInputs()
    val booking = bookingService.getBooking(sourceData, noInputs)

    val supportedResponse = validationService.validateSupportedSentencesAndCalculations(sourceData)
    if (
      supportedResponse.unsupportedSentenceMessages.isNotEmpty() ||
      supportedResponse.unsupportedCalculationMessages.isNotEmpty()
    ) {
      return supportedResponse
    }
    val bookingValidationMessages = validationService.validateBeforeCalculation(booking)

    if (bookingValidationMessages.isNotEmpty()) {
      log.error("Unexpected: manual entry journey should not be triggered by pre-calculation validation at this stage.")
      log.error(
        "Pre-calculation validation unexpectedly failed in supportedValidation for prisonerId=$prisonerId. " +
          "Messages: ${bookingValidationMessages.joinToString("; ") { it.message }}",
      )
      return supportedResponse
    }
    val calculationOutput = calculationService.calculateReleaseDates(
      booking,
      noInputs,
    )

    val manualEntryMessages = validationService.validateManualEntryJourneyRequirements(booking, calculationOutput)

    return supportedResponse.copy(
      unsupportedManualMessages = manualEntryMessages,
    )
  }

  @Transactional
  fun calculate(
    prisonerId: String,
    calculationRequestModel: CalculationRequestModel,
    inactiveDataOptions: InactiveDataOptions = InactiveDataOptions.default(),
    calculationType: CalculationStatus = PRELIMINARY,
  ): CalculatedReleaseDates {
    val sourceData = calculationSourceDataService.getCalculationSourceData(prisonerId, inactiveDataOptions)
    return calculate(sourceData, calculationRequestModel, calculationType)
  }

  @Transactional
  fun calculate(
    sourceData: CalculationSourceData,
    calculationRequestModel: CalculationRequestModel,
    calculationType: CalculationStatus = PRELIMINARY,
  ): CalculatedReleaseDates {
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
    val sourceData = calculationSourceDataService.getCalculationSourceData(calculationRequest.prisonerId, InactiveDataOptions.default())
    val userInput = transform(calculationRequest.calculationRequestUserInput)
    val booking = bookingService.getBooking(sourceData, userInput)
    val currentBookingJson = objectToJson(booking, objectMapper)
    val preliminaryBookingJson = calculationRequest.inputData

    if (preliminaryBookingJson.hashCode() != currentBookingJson.hashCode()) {
      throw PreconditionFailedException("The booking data used for the preliminary calculation has changed")
    }

    val validationErrors = validationService.validateBeforeCalculation(sourceData, userInput)

    if (validationErrors.any { !it.type.excludedInSave() }) {
      throw CrdWebException(message = "The booking now fails validation", status = HttpStatus.INTERNAL_SERVER_ERROR)
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
    sourceData: CalculationSourceData,
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
        calculationConfirmationService.storeApprovedDates(calculation, approvedDates)
      }
      calculationConfirmationService.writeToNomisAndPublishEvent(prisonerId, booking, calculation, approvedDates, isSpecialistSupport)
      return calculation
    } catch (error: Exception) {
      recordError(booking, sourceData, userInput, reasonForCalculation, otherReasonForCalculation)
      throw error
    }
  }

  @Transactional
  fun calculate(
    booking: Booking,
    calculationStatus: CalculationStatus,
    sourceData: CalculationSourceData,
    reasonForCalculation: CalculationReason?,
    calculationUserInputs: CalculationUserInputs,
    otherCalculationReason: String? = null,
    calculationFragments: CalculationFragments? = null,
    calculationType: CalculationType = CalculationType.CALCULATED,
    historicalTusedSource: HistoricalTusedSource? = null,
    usernameOverride: String? = null,
  ): CalculatedReleaseDates {
    val calculationRequest = calculationRequestRepository.save(
      transform(
        booking,
        usernameOverride ?: serviceUserService.getUsername(),
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
    val calculationDates = calculationResult.dates.toMutableMap()

    if (featureToggles.historicSled) {
      val sledDate = calculationDates[ReleaseDateType.SLED]
      val historicDates = sledDate?.let { historicDatesFromSled(sourceData.prisonerDetails.offenderNo, it) }
      if (sledDate !== null && historicDates !== null) {
        persistCalculationDatesWithHistoricOverrides(
          sledDate,
          calculationRequest,
          calculationResult,
          historicDates,
          calculationDates,
        )
      } else {
        persistCalculationDates(calculationRequest, calculationDates)
      }
    } else {
      persistCalculationDates(calculationRequest, calculationDates)
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
      dates = calculationDates,
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

  fun historicDatesFromSled(
    offenderNo: String,
    sledDate: LocalDate?,
  ): List<CalculationOutcome>? = sledDate?.let {
    calculationOutcomeRepository
      .getDominantHistoricDates(offenderNo, it)
      .takeIf { results -> results.isNotEmpty() }
  }

  private fun persistCalculationDates(
    calculationRequest: CalculationRequest,
    calculationDates: Map<ReleaseDateType, LocalDate>,
  ) = calculationDates.forEach { (type, date) ->
    calculationOutcomeRepository.save(transform(calculationRequest, type, date))
  }

  /**
   * Persist new outcome dates using historic outcome dates where present
   * Create CalculationOutcomeHistoricOverride for each
   */
  private fun persistCalculationDatesWithHistoricOverrides(
    sledDate: LocalDate,
    calculationRequest: CalculationRequest,
    calculationResult: CalculationResult,
    historicDates: List<CalculationOutcome>,
    calculationDates: MutableMap<ReleaseDateType, LocalDate>,
  ) {
    val overridesByType = dominantHistoricDateService.calculateFromSled(sledDate, historicDates)

    /**
     * Add SLED to calculation dates if historic dates include LED and SED with the same date
     * We need to combine the two dates to a SLED in such an instance
     */
    overridesByType[ReleaseDateType.SLED]?.let { sledOverride ->
      if (historicDates.none { it.calculationDateType == ReleaseDateType.SLED.name }) {
        calculationDates[ReleaseDateType.SLED] = sledOverride
      }
    }

    val historicOverrideIds = historicDates
      .filter { overridesByType.containsKey(ReleaseDateType.valueOf(it.calculationDateType)) }
      .associate { ReleaseDateType.valueOf(it.calculationDateType) to it.id }

    overridesByType.forEach { (type, date) -> calculationDates[type] = date }

    if (ReleaseDateType.SLED !in overridesByType) {
      calculationDates.remove(ReleaseDateType.SLED)
    }

    val newOutcomesByType = calculationDates.map { (type, date) ->
      val outcome = calculationOutcomeRepository.save(transform(calculationRequest, type, date))
      ReleaseDateType.valueOf(outcome.calculationDateType) to outcome
    }.toMap()

    overridesByType.forEach { (type, historicDate) ->
      val historicId = historicOverrideIds[type] ?: return@forEach
      val resultDate = calculationResult.dates[type] ?: return@forEach
      val calculationOutcome = newOutcomesByType[type] ?: return@forEach
      val overrideRecord = CalculationOutcomeHistoricOverride(
        calculationRequestId = calculationRequest.id,
        calculationOutcomeDate = resultDate,
        historicCalculationOutcomeId = historicId,
        historicCalculationOutcomeDate = historicDate,
        calculationOutcome = calculationOutcome,
      )
      calculationOutcomeHistoricOverrideRepository.save(overrideRecord)
    }
  }

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
    return sourceDataMapper.mapSentencesAndOffences(calculationRequest)
  }

  @Transactional(readOnly = true)
  fun findPrisonerDetailsFromCalculation(calculationRequestId: Long): PrisonerDetails {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    if (calculationRequest.prisonerDetails == null) {
      throw PrisonApiDataNotFoundException("Prisoner details data not found for calculation $calculationRequestId")
    }
    return sourceDataMapper.mapPrisonerDetails(calculationRequest)
  }

  @Transactional(readOnly = true)
  fun findBookingAndSentenceAdjustmentsFromCalculation(calculationRequestId: Long): BookingAndSentenceAdjustments {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    if (calculationRequest.adjustments == null) {
      throw PrisonApiDataNotFoundException("Adjustments data not found for calculation $calculationRequestId")
    }
    return sourceDataMapper.mapBookingAndSentenceAdjustments(calculationRequest)
  }

  @Transactional(readOnly = true)
  fun findAdjustmentsFromCalculation(calculationRequestId: Long): List<AdjustmentDto> {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    if (calculationRequest.adjustments == null) {
      throw PrisonApiDataNotFoundException("Adjustments data not found for calculation $calculationRequestId")
    }
    return sourceDataMapper.mapAdjustments(calculationRequest)
  }

  @Transactional(readOnly = true)
  fun findReturnToCustodyDateFromCalculation(calculationRequestId: Long): ReturnToCustodyDate? {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    if (calculationRequest.returnToCustodyDate == null) {
      return null
    }
    return sourceDataMapper.mapReturnToCustodyDate(calculationRequest)
  }

  private fun getCalculationRequest(calculationRequestId: Long): CalculationRequest = calculationRequestRepository.findById(calculationRequestId).orElseThrow {
    EntityNotFoundException("No calculation results exist for calculationRequestId $calculationRequestId ")
  }

  private fun getCalculationRequestByReference(calculationReference: String): CalculationRequest = calculationRequestRepository.findByCalculationReference(UUID.fromString(calculationReference)).orElseThrow {
    EntityNotFoundException("No calculation results exist for calculationReference $calculationReference ")
  }

  @Transactional(readOnly = true)
  fun findCalculationResultsByCalculationReference(
    calculationReference: String,
    checkForChange: Boolean = false,
  ): CalculatedReleaseDates {
    val calculationRequest = getCalculationRequestByReference(calculationReference)
    if (checkForChange) {
      log.info("Checking for change in data")
      val sourceData = calculationSourceDataService.getCalculationSourceData(calculationRequest.prisonerId, InactiveDataOptions.default())
      val userInput = transform(calculationRequest.calculationRequestUserInput)
      val booking = bookingService.getBooking(sourceData, userInput)

      if (calculationRequest.inputData.hashCode() != objectToJson(booking, objectMapper).hashCode()) {
        throw CalculationDataHasChangedError(calculationReference, calculationRequest.prisonerId)
      }
    }
    return transform(calculationRequest)
  }

  @Transactional(readOnly = true)
  fun validateForManualBooking(prisonerId: String): List<ValidationMessage> {
    val sourceData = calculationSourceDataService.getCalculationSourceData(prisonerId, InactiveDataOptions.default())
    return validationService.validateSentenceForManualEntry(sourceData.sentenceAndOffences)
  }

  @Transactional(readOnly = true)
  fun validateRequestedDates(dates: List<String>): List<ValidationMessage> = validationService.validateRequestedDates(dates)

  fun recordError(
    booking: Booking,
    sourceData: CalculationSourceData,
    calculationUserInputs: CalculationUserInputs?,
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

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
