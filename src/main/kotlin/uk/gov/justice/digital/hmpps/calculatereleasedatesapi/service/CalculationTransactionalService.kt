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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcomeHistoricSledOverride
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.TrancheOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.ERROR
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTrancheCategory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.BreakdownChangedSinceLastCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CalculationDataHasChangedError
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PreconditionFailedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PrisonApiDataNotFoundException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationReasonDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationRequestModel
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManuallyEnteredDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmitCalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeHistoricOverrideRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.TrancheOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.service.ValidationService
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
  private val buildProperties: BuildProperties,
  private val trancheOutcomeRepository: TrancheOutcomeRepository,
  private val featureToggles: FeatureToggles,
  private val sentenceLevelDatesService: SentenceLevelDatesService,
) {

  @Transactional
  fun calculate(
    prisonerId: String,
    calculationRequestModel: CalculationRequestModel,
    sourceDataLookupOptions: SourceDataLookupOptions = SourceDataLookupOptions.default(),
    calculationStatus: CalculationStatus = PRELIMINARY,
  ): CalculatedReleaseDates {
    val sourceData = calculationSourceDataService.getCalculationSourceData(prisonerId, sourceDataLookupOptions)
    val calculationUserInputs = calculationRequestModel.calculationUserInputs ?: CalculationUserInputs()
    val booking = bookingService.getBooking(sourceData)
    val reasonForCalculation = calculationReasonRepository.findById(calculationRequestModel.calculationReasonId)
      .orElse(null) // TODO: This should thrown an EntityNotFoundException when the reason is mandatory.
    try {
      return calculate(
        booking,
        calculationStatus,
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
    val sourceData = calculationSourceDataService.getCalculationSourceData(calculationRequest.prisonerId, SourceDataLookupOptions.default())
    val userInput = transform(calculationRequest.calculationRequestUserInput)
    val booking = bookingService.getBooking(sourceData)
    val currentBookingJson = objectToJson(booking, objectMapper)
    val preliminaryBookingJson = calculationRequest.inputData

    if (preliminaryBookingJson.hashCode() != currentBookingJson.hashCode()) {
      throw PreconditionFailedException("The booking data used for the preliminary calculation has changed")
    }

    val validationErrors = validationService.validate(sourceData, userInput, ValidationOrder.INVALID)

    if (validationErrors.any { !it.type.excludedInSave() }) {
      throw CrdWebException(message = "The booking now fails validation", status = HttpStatus.INTERNAL_SERVER_ERROR)
    }

    return confirmCalculation(
      calculationRequest.prisonerId,
      submitCalculationRequest.calculationFragments, sourceData, booking, userInput,
      submitCalculationRequest.approvedDates,
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
    approvedDates: List<ManuallyEnteredDate>?,
    reasonForCalculation: CalculationReason?,
    otherReasonForCalculation: String?,
    historicalTusedSource: HistoricalTusedSource? = null,
  ): CalculatedReleaseDates {
    try {
      val calculation = calculate(
        booking,
        CONFIRMED,
        sourceData,
        reasonForCalculation,
        userInput,
        otherReasonForCalculation,
        calculationFragments,
        CalculationType.CALCULATED,
        historicalTusedSource,
      )
      if (!approvedDates.isNullOrEmpty()) {
        calculationConfirmationService.storeApprovedDates(calculation, approvedDates)
      }
      calculationConfirmationService.writeToNomisAndPublishEvent(prisonerId, booking, calculation, approvedDates)
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
        buildProperties.version ?: "",
      ),
    )

    val calculationOutput = calculationService.calculateReleaseDates(booking, calculationUserInputs)
    val calculationResult = calculationOutput.calculationResult

    calculationResult.dates.forEach { (type, date) ->
      calculationOutcomeRepository.save(transform(calculationRequest, type, date))
    }

    val sds40TrancheName = calculationResult.trancheAllocationByCategory[SDSEarlyReleaseTrancheCategory.SDS40] ?: SDSEarlyReleaseTranche.TRANCHE_0
    trancheOutcomeRepository.save(
      TrancheOutcome(
        calculationRequest = calculationRequest,
        allocatedTranche = sds40TrancheName,
        tranche = sds40TrancheName,
        affectedBySds40 = calculationResult.affectedBySds40,
        ftr56Tranche = calculationResult.trancheAllocationByCategory[SDSEarlyReleaseTrancheCategory.FTR56] ?: SDSEarlyReleaseTranche.FTR_56_TRANCHE_0,
      ),
    )

    calculationOutput.calculationResult.usedPreviouslyRecordedSLED?.let {
      val overrideRecord = CalculationOutcomeHistoricSledOverride(
        calculationRequestId = calculationRequest.id(),
        calculationOutcomeDate = it.calculatedDate,
        historicCalculationRequestId = it.previouslyRecordedSLEDCalculationRequestId,
        historicCalculationOutcomeDate = it.previouslyRecordedSLEDDate,
      )
      calculationOutcomeHistoricOverrideRepository.save(overrideRecord)
    }

    if (featureToggles.storeSentenceLevelDates) {
      sentenceLevelDatesService.storeSentenceLevelDates(calculationOutput.sentenceLevelDates, sourceData, calculationRequest)
    }

    return CalculatedReleaseDates(
      dates = calculationResult.dates,
      effectiveSentenceLength = calculationResult.effectiveSentenceLength,
      prisonerId = sourceData.prisonerDetails.offenderNo,
      bookingId = sourceData.prisonerDetails.bookingId,
      calculationFragments = calculationFragments,
      calculationRequestId = calculationRequest.id(),
      calculationStatus = calculationStatus,
      approvedDates = null,
      calculationReference = calculationRequest.calculationReference,
      calculationReason = reasonForCalculation?.let { CalculationReasonDto.from(it) },
      otherReasonDescription = otherCalculationReason,
      calculationDate = calculationRequest.calculatedAt.toLocalDate(),
      historicalTusedSource = calculationResult.historicalTusedSource,
      calculationOutput = calculationOutput,
      usedPreviouslyRecordedSLED = calculationOutput.calculationResult.usedPreviouslyRecordedSLED,
    )
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
      val sourceData = calculationSourceDataService.getCalculationSourceData(calculationRequest.prisonerId, SourceDataLookupOptions.default())
      val booking = bookingService.getBooking(sourceData)

      if (calculationRequest.inputData.hashCode() != objectToJson(booking, objectMapper).hashCode()) {
        throw CalculationDataHasChangedError(calculationReference, calculationRequest.prisonerId)
      }
    }
    return transform(calculationRequest)
  }

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
        version = buildProperties.version ?: "",
      ),
    )
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
