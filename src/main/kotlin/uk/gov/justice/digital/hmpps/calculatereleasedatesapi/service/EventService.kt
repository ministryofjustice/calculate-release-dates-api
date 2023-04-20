package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.amazonaws.services.sns.model.MessageAttributeValue
import com.amazonaws.services.sns.model.PublishRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class EventService(
  private val hmppsQueueService: HmppsQueueService,
  private val mapper: ObjectMapper,
) {

  private val domainTopic by lazy {
    hmppsQueueService.findByTopicId("hmppseventtopic") ?: throw IllegalStateException("hmppseventtopic topic not found")
  }

  fun publishReleaseDatesChangedEvent(prisonerId: String, bookingId: Long) {
    val event = ReleaseDateChangedEvent(additionalInformation = (CalculateReleaseDatesAdditionalInformation(prisonerId, bookingId)))
    domainTopic.snsClient.publish(
      PublishRequest(domainTopic.arn, mapper.writeValueAsString(event))
        .addMessageAttributesEntry("eventType", MessageAttributeValue().withDataType("String").withStringValue(event.eventType)),
    )
    log.info("Published 'release dates changed event' for: $prisonerId")
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

data class CalculateReleaseDatesAdditionalInformation(
  val prisonerId: String,
  val bookingId: Long,
)

abstract class CalculateReleaseDatesDomainEvent {
  abstract val additionalInformation: CalculateReleaseDatesAdditionalInformation
  val occurredAt: String =
    DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Europe/London")).format(Instant.now())
  abstract val eventType: String
  abstract val version: Int
  abstract var description: String
}

class ReleaseDateChangedEvent(
  override val eventType: String = "calculate-release-dates.prisoner.changed",
  override var description: String = "Prisoners release dates have been re-calculated",
  override val additionalInformation: CalculateReleaseDatesAdditionalInformation,
  override val version: Int = 1,
) : CalculateReleaseDatesDomainEvent()
