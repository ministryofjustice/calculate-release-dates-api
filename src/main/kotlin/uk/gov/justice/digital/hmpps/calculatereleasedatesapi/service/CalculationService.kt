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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import javax.persistence.EntityNotFoundException

@Service
class CalculationService(
  private val bookingCalculationService: BookingCalculationService,
  private val bookingExtractionService: BookingExtractionService,
  private val bookingTimelineService: BookingTimelineService,
) {

  fun calculateReleaseDates(booking: Booking): Pair<Booking, BookingCalculation> {
    val workingBooking = calculate(booking)
    // apply any rules to calculate the dates
    return workingBooking to bookingExtractionService.extract(workingBooking)
  }

  private fun calculate(booking: Booking): Booking {
    var workingBooking: Booking = booking

    // identify the types of the sentences
    workingBooking =
      bookingCalculationService
        .identify(workingBooking)

    // calculate the dates within the sentences (Generate initial sentence calculations)
    workingBooking =
      bookingCalculationService
        .calculate(workingBooking)

    workingBooking =
      bookingCalculationService
        .createConsecutiveSentences(workingBooking)

    workingBooking =
      bookingCalculationService
        .createSingleTermSentences(workingBooking)

    workingBooking = bookingTimelineService
      .walkTimelineOfBooking(workingBooking)

    return workingBooking
  }

}
