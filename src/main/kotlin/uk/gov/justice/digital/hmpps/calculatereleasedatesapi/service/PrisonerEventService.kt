package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.transaction.Transactional
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.event.PrisonerBookingMovedEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.event.PrisonerMergedEvent
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerModifiedReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Service
class PrisonerEventService(
  private val featureToggles: FeatureToggles,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val approvedDatesSubmissionRepository: ApprovedDatesSubmissionRepository,
  private val eventService: EventService,
) {

  @Transactional
  fun handlePrisonerMerged(event: PrisonerMergedEvent) {
    if (featureToggles.moveAndMergeEventsEnabled) {
      LOGGER.info("Handling prisoner merge event from ${event.additionalInformation.removedNomsNumber} to ${event.additionalInformation.nomsNumber}.")
      calculationRequestRepository.mergePrisoners(fromPrisoner = event.additionalInformation.removedNomsNumber, toPrisoner = event.additionalInformation.nomsNumber, reason = PrisonerModifiedReason.PRISONER_MERGE)
      approvedDatesSubmissionRepository.mergePrisoners(fromPrisoner = event.additionalInformation.removedNomsNumber, toPrisoner = event.additionalInformation.nomsNumber, reason = PrisonerModifiedReason.PRISONER_MERGE)
      eventService.publishReleaseDatesChangedEvent(event.additionalInformation.nomsNumber)
      eventService.publishReleaseDatesChangedEvent(event.additionalInformation.removedNomsNumber)
    } else {
      LOGGER.info("Ignoring prisoner merge event from ${event.additionalInformation.removedNomsNumber} to ${event.additionalInformation.nomsNumber} as the feature toggle is off.")
    }
  }

  @Transactional
  fun handleBookingMoved(event: PrisonerBookingMovedEvent) {
    if (featureToggles.moveAndMergeEventsEnabled) {
      LOGGER.info("Handling booking move event for booking ${event.additionalInformation.bookingId} from ${event.additionalInformation.movedFromNomsNumber} to ${event.additionalInformation.movedToNomsNumber}.")
      calculationRequestRepository.moveBooking(bookingId = event.additionalInformation.bookingId.toLong(), fromPrisoner = event.additionalInformation.movedFromNomsNumber, toPrisoner = event.additionalInformation.movedToNomsNumber, reason = PrisonerModifiedReason.BOOKING_MOVE)
      approvedDatesSubmissionRepository.moveBooking(bookingId = event.additionalInformation.bookingId.toLong(), fromPrisoner = event.additionalInformation.movedFromNomsNumber, toPrisoner = event.additionalInformation.movedToNomsNumber, reason = PrisonerModifiedReason.BOOKING_MOVE)
      eventService.publishReleaseDatesChangedEvent(event.additionalInformation.movedFromNomsNumber)
      eventService.publishReleaseDatesChangedEvent(event.additionalInformation.movedToNomsNumber)
    } else {
      LOGGER.info("Ignoring booking move event for booking ${event.additionalInformation.bookingId} from ${event.additionalInformation.movedFromNomsNumber} to ${event.additionalInformation.movedToNomsNumber} as the feature toggle is off.")
    }
  }

  companion object {
    private val LOGGER: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
