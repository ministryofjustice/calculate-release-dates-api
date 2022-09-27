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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceDiagram
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import javax.persistence.EntityNotFoundException

@Service
class CalculationTransactionalService(
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationOutcomeRepository: CalculationOutcomeRepository,
  private val objectMapper: ObjectMapper,
  private val prisonService: PrisonService,
  private val domainEventPublisher: DomainEventPublisher,
  private val prisonApiDataMapper: PrisonApiDataMapper,
  private val calculationService: CalculationService
) {

  fun getCurrentAuthentication(): AuthAwareAuthenticationToken =
    SecurityContextHolder.getContext().authentication as AuthAwareAuthenticationToken?
      ?: throw IllegalStateException("User is not authenticated")

  @Transactional
  fun calculate(booking: Booking, calculationStatus: CalculationStatus, sourceData: PrisonApiSourceData, calculationUserInputs: CalculationUserInputs?, calculationFragments: CalculationFragments? = null): CalculatedReleaseDates {
    val calculationRequest =
      calculationRequestRepository.save(
        transform(booking, getCurrentAuthentication().principal, calculationStatus, sourceData, objectMapper, calculationUserInputs, calculationFragments)
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
  fun calculateWithBreakdown(booking: Booking, previousCalculationResults: CalculatedReleaseDates): CalculationBreakdown {
    val (workingBooking, bookingCalculation) = calculationService.calculateReleaseDates(booking)
    if (bookingCalculation.dates == previousCalculationResults.dates) {
      return transform(workingBooking, bookingCalculation.breakdownByReleaseDateType, bookingCalculation.otherDates)
    } else {
      throw BreakdownChangedSinceLastCalculation("Calculation no longer agrees with algorithm.")
    }
  }
  @Transactional(readOnly = true)
  fun calculateWithDiagram(booking: Booking, previousCalculationResults: CalculatedReleaseDates): SentenceDiagram {
    val (workingBooking, bookingCalculation) = calculationService.calculateReleaseDates(booking)
    if (bookingCalculation.dates == previousCalculationResults.dates) {
      return transform(workingBooking)
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
  fun findUserInput(calculationRequestId: Long): CalculationUserInputs? {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    return transform(calculationRequest.calculationRequestUserInputs)
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

  @Transactional(readOnly = true)
  fun findOffenderFinePaymentsFromCalculation(calculationRequestId: Long): List<OffenderFinePayment> {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    if (calculationRequest.offenderFinePayments == null) {
      return listOf()
    }
    return prisonApiDataMapper.mapOffenderFinePayment(calculationRequest)
  }
  private fun getCalculationRequest(calculationRequestId: Long): CalculationRequest {
    return calculationRequestRepository.findById(calculationRequestId).orElseThrow {
      EntityNotFoundException("No calculation results exist for calculationRequestId $calculationRequestId ")
    }
  }

  @Transactional(readOnly = true)
  fun getBooking(calculationRequestId: Long): Booking {
    val calculationRequest =
      calculationRequestRepository.findById(calculationRequestId).orElseThrow {
        EntityNotFoundException("No calculation results exist for calculationRequestId $calculationRequestId ")
      }

    return objectMapper.treeToValue(calculationRequest.inputData, Booking::class.java)
  }

  @Transactional(readOnly = true)
  fun validateConfirmationRequest(calculationRequestId: Long, booking: Booking) {
    val calculationRequest =
      calculationRequestRepository.findByIdAndCalculationStatus(
        calculationRequestId,
        PRELIMINARY.name
      ).orElseThrow {
        EntityNotFoundException("No preliminary calculation exists for calculationRequestId $calculationRequestId")
      }

    if (calculationRequest.inputData.hashCode() != objectToJson(booking, objectMapper).hashCode()) {
      throw PreconditionFailedException("The booking data used for the preliminary calculation has changed")
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
  fun recordError(booking: Booking, sourceData: PrisonApiSourceData, calculationUserInputs: CalculationUserInputs?, error: Exception) {
    calculationRequestRepository.save(
      transform(booking, getCurrentAuthentication().principal, ERROR, sourceData, objectMapper, calculationUserInputs)
    )
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
