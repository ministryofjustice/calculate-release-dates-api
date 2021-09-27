package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.amazonaws.services.sns.AmazonSNS
import com.google.gson.Gson
import org.slf4j.LoggerFactory
import org.springframework.cloud.aws.messaging.core.NotificationMessagingTemplate
import org.springframework.cloud.aws.messaging.core.TopicMessageChannel
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DomainEvent(
  val eventType: String,
  val additionalInformation: ReleaseDatesChanged,
  val version: Int,
  val occurredAt: String,
  val publishedAt: String,
  val description: String
)

data class ReleaseDatesChanged(val prisonerId: String, val bookingId: Long)

interface DomainEventPublisher {
  fun publishReleaseDateChange(prisonerId: String, bookingId: Long)
}

class StubDomainEventPublisher : DomainEventPublisher {
  override fun publishReleaseDateChange(prisonerId: String, bookingId: Long) {
    log.info("Publishing release date change in stub for prisoner {} and booking {}", prisonerId, bookingId)
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

class DomainEventPublisherImpl(client: AmazonSNS, topicArn: String, private val gson: Gson) :
  DomainEventPublisher {

  private val topicTemplate = NotificationMessagingTemplate(client)
  private val topicMessageChannel = TopicMessageChannel(client, topicArn)

  override fun publishReleaseDateChange(prisonerId: String, bookingId: Long) {
    log.info("Publishing release date change for prisoner {} and booking {}", prisonerId, bookingId)
    val now = LocalDateTime.now()
      .atZone(ZoneId.of("Europe/London"))
      .toOffsetDateTime()
      .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    val domainEvent = DomainEvent(
      eventType = "calculate-release-dates.prisoner.changed",
      version = 1,
      occurredAt = now,
      publishedAt = now,
      description = "Prisoners release dates have been re-calculated",
      additionalInformation = ReleaseDatesChanged(prisonerId, bookingId)
    )

    val payload = gson.toJson(domainEvent)

    topicTemplate.convertAndSend(
      topicMessageChannel,
      payload,
      mapOf("eventType" to domainEvent.eventType)
    )
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
