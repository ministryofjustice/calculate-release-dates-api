package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.AuthAwareAuthenticationToken
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.ERROR
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.BreakdownChangedSinceLastCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PreconditionFailedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PrisonApiDataNotFoundException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import javax.persistence.EntityNotFoundException

@Service
class CalculationTransactionalService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
  private val objectMapper: ObjectMapper,
  private val prisonService: PrisonService,
  private val domainEventPublisher: DomainEventPublisher,
  private val prisonApiDataMapper: PrisonApiDataMapper,
  private val calculationService: CalculationService,
  private val bookingService: BookingService,
  private val validationService: ValidationService,
) {

  fun getCurrentAuthentication(): AuthAwareAuthenticationToken =
    SecurityContextHolder.getContext().authentication as AuthAwareAuthenticationToken?
      ?: throw IllegalStateException("User is not authenticated")

  /*
   * There are 3 stages of full validation:
   * 1. validate user input data, the raw sentence and offence data from NOMIS
   * 2. validate the raw Booking (NOMIS offence data transformed into a Booking object)
   * 3. Run the calculation and catch any errors thrown by the calculation algorithm
   * 4. Validate the post calculation Booking (The Booking is transformed during the calculation). e.g. Consecutive sentences (aggregates)
   *
   * activeDataOnly is only used by the test 1000 calcs functionality
   */
  fun fullValidation(prisonerId: String, calculationUserInputs: CalculationUserInputs, activeDataOnly: Boolean = true): List<ValidationMessage> {
    val sourceData = prisonService.getPrisonApiSourceData(prisonerId, activeDataOnly)
    var messages = validationService.validateBeforeCalculation(sourceData, calculationUserInputs) // Validation stage 1 of 3
    if (messages.isNotEmpty()) return messages
    // getBooking relies on the previous validation stage to have succeeded
    val booking = bookingService.getBooking(sourceData, calculationUserInputs)
    messages = validationService.validateBeforeCalculation(booking) // Validation stage 2 of 4
    if (messages.isNotEmpty()) return messages
    val bookingAfterCalculation = calculationService.calculate(booking) // Validation stage 3 of 4
    messages = validationService.validateBookingAfterCalculation(bookingAfterCalculation) // Validation stage 4 of 4
    return messages
  }

  //  The activeDataOnly flag is only used by a test endpoint (1000 calcs test, which is used to test historic data)
  @Transactional
  fun calculate(prisonerId: String, calculationUserInputs: CalculationUserInputs, activeDataOnly: Boolean = true, calculationType: CalculationStatus = PRELIMINARY): CalculatedReleaseDates {
    val sourceData = prisonService.getPrisonApiSourceData(prisonerId, activeDataOnly)
    val booking = bookingService.getBooking(sourceData, calculationUserInputs)
    try {
      return calculate(booking, calculationType, sourceData, calculationUserInputs)
    } catch (error: Exception) {
      recordError(booking, sourceData, calculationUserInputs, error)
      throw error
    }
  }

  @Transactional
  fun validateAndConfirmCalculation(
    calculationRequestId: Long,
    calculationFragments: CalculationFragments,
  ): CalculatedReleaseDates {
    val calculationRequest =
      calculationRequestRepository.findByIdAndCalculationStatus(
        calculationRequestId,
        PRELIMINARY.name
      ).orElseThrow {
        EntityNotFoundException("No preliminary calculation exists for calculationRequestId $calculationRequestId")
      }
    val sourceData = prisonService.getPrisonApiSourceData(calculationRequest.prisonerId)
    val userInput = transform(calculationRequest.calculationRequestUserInput)
    val booking = bookingService.getBooking(sourceData, userInput)

    if (calculationRequest.inputData.hashCode() != objectToJson(booking, objectMapper).hashCode()) {
      throw PreconditionFailedException("The booking data used for the preliminary calculation has changed")
    }

    if (validationService.validateBeforeCalculation(sourceData, userInput).isNotEmpty()) {
      throw PreconditionFailedException("The booking now fails validation")
    }

    return confirmCalculation(calculationRequest.prisonerId, calculationFragments, sourceData, booking, userInput)
  }

  private fun confirmCalculation(
    prisonerId: String,
    calculationFragments: CalculationFragments,
    sourceData: PrisonApiSourceData,
    booking: Booking,
    userInput: CalculationUserInputs?
  ): CalculatedReleaseDates {
    try {
      val calculation =
        calculate(booking, CONFIRMED, sourceData, userInput, calculationFragments)
      writeToNomisAndPublishEvent(prisonerId, booking, calculation)
      return calculation
    } catch (error: Exception) {
      recordError(booking, sourceData, userInput, error)
      throw error
    }
  }

  // TODO Only called privately but is used by tests. Could be marked private if tests are refactored
  @Transactional
  fun calculate(
    booking: Booking,
    calculationStatus: CalculationStatus,
    sourceData: PrisonApiSourceData,
    calculationUserInputs: CalculationUserInputs?,
    calculationFragments: CalculationFragments? = null
  ): CalculatedReleaseDates {
    val calculationRequest =
      calculationRequestRepository.save(
        transform(
          booking,
          getCurrentAuthentication().principal,
          calculationStatus,
          sourceData,
          objectMapper,
          calculationUserInputs,
          calculationFragments
        )
      )

    val calculationResult = calculationService.calculateReleaseDates(booking).second

    calculationResult.dates.forEach {
      calculationOutcomeRepository.save(transform(calculationRequest, it.key, it.value))
    }

    return CalculatedReleaseDates(
      dates = calculationResult.dates,
      effectiveSentenceLength = calculationResult.effectiveSentenceLength,
      prisonerId = sourceData.prisonerDetails.offenderNo,
      bookingId = sourceData.prisonerDetails.bookingId,
      calculationFragments = calculationFragments,
      calculationRequestId = calculationRequest.id,
      calculationStatus = calculationStatus
    )
  }

  @Transactional(readOnly = true)
  fun calculateWithBreakdown(
    booking: Booking,
    previousCalculationResults: CalculatedReleaseDates
  ): CalculationBreakdown {
    val (workingBooking, bookingCalculation) = calculationService.calculateReleaseDates(booking)
    if (bookingCalculation.dates == previousCalculationResults.dates) {
      return transform(workingBooking, bookingCalculation.breakdownByReleaseDateType, bookingCalculation.otherDates)
    } else {
      throw BreakdownChangedSinceLastCalculation("Calculation no longer agrees with algorithm.")
    }
  }

  @Transactional(readOnly = true)
  fun findConfirmedCalculationResults(prisonerId: String, bookingId: Long): CalculatedReleaseDates {
    val calculationRequest =
      calculationRequestRepository.findFirstByPrisonerIdAndBookingIdAndCalculationStatusOrderByCalculatedAtDesc(
        prisonerId,
        bookingId,
        CONFIRMED.name
      ).orElseThrow {
        EntityNotFoundException("No confirmed calculation exists for prisoner $prisonerId and bookingId $bookingId")
      }

    return transform(calculationRequest)
  }

  @Transactional(readOnly = true)
  fun findCalculationResults(calculationRequestId: Long): CalculatedReleaseDates {
    return transform(getCalculationRequest(calculationRequestId))
  }

  @Transactional(readOnly = true)
  fun findUserInput(calculationRequestId: Long): CalculationUserInputs {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    return transform(calculationRequest.calculationRequestUserInput)
  }

  @Transactional(readOnly = true)
  fun findSentenceAndOffencesFromCalculation(calculationRequestId: Long): List<SentenceAndOffences> {
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

  private fun getCalculationRequest(calculationRequestId: Long): CalculationRequest {
    return calculationRequestRepository.findById(calculationRequestId).orElseThrow {
      EntityNotFoundException("No calculation results exist for calculationRequestId $calculationRequestId ")
    }
  }

  @Transactional(readOnly = true)
  fun writeToNomisAndPublishEvent(prisonerId: String, booking: Booking, calculation: CalculatedReleaseDates) {
    val calculationRequest = calculationRequestRepository.findById(calculation.calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists") }

    val updateOffenderDates = UpdateOffenderDates(
      calculationUuid = calculationRequest.calculationReference,
      submissionUser = getCurrentAuthentication().principal,
      keyDates = transform(calculation)
    )
    try {
      prisonService.postReleaseDates(booking.bookingId, updateOffenderDates)
    } catch (ex: Exception) {
      log.error("Nomis write failed: ${ex.message}")
      throw EntityNotFoundException(
        "Writing release dates to NOMIS failed for prisonerId $prisonerId " +
          "and bookingId ${booking.bookingId}"
      )
    }
    try {
      domainEventPublisher.publishReleaseDateChange(prisonerId, booking.bookingId)
    } catch (ex: Exception) {
      // This doesn't constitute a failure at the moment because we are writing back to NOMIS using a POST endpoint.
      // Eventually the event will be used to write back to NOMIS and then this will need refactoring
      log.info(
        "Publishing the release date change to the domain event topic failed for prisonerId $prisonerId " +
          "and bookingId ${booking.bookingId}"
      )
    }
  }

  @Transactional
  fun recordError(
    booking: Booking,
    sourceData: PrisonApiSourceData,
    calculationUserInputs: CalculationUserInputs?,
    error: Exception
  ) {
    calculationRequestRepository.save(
      transform(booking, getCurrentAuthentication().principal, ERROR, sourceData, objectMapper, calculationUserInputs)
    )
  }

  @Transactional(readOnly = true)
  fun getCalculationBreakdown(
    calculationRequestId: Long
  ): CalculationBreakdown {
    val calculationUserInputs = findUserInput(calculationRequestId)
    val prisonerDetails = findPrisonerDetailsFromCalculation(calculationRequestId)
    val sentenceAndOffences = findSentenceAndOffencesFromCalculation(calculationRequestId)
    val bookingAndSentenceAdjustments = findBookingAndSentenceAdjustmentsFromCalculation(calculationRequestId)
    val returnToCustodyDate = findReturnToCustodyDateFromCalculation(calculationRequestId)
    val calculation = findCalculationResults(calculationRequestId)
    val booking = Booking(
      offender = transform(prisonerDetails),
      sentences = sentenceAndOffences.map { transform(it, calculationUserInputs) }.flatten(),
      adjustments = transform(bookingAndSentenceAdjustments, sentenceAndOffences),
      bookingId = prisonerDetails.bookingId,
      returnToCustodyDate = returnToCustodyDate?.returnToCustodyDate,
      calculateErsed = calculationUserInputs.calculateErsed
    )
    return calculateWithBreakdown(booking, calculation)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
