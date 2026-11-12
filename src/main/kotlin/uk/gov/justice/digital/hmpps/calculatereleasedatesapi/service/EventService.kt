package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.event.CalculateReleaseDatesAdditionalInformation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.event.ReleaseDateChangedEvent
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class EventService(
  private val hmppsQueueService: HmppsQueueService,
  private val mapper: ObjectMapper,
) {

  private val domainTopic by lazy {
    hmppsQueueService.findByTopicId("hmppseventtopic") ?: throw IllegalStateException("hmppseventtopic topic not found")
  }

  fun publishReleaseDatesChangedEvent(prisonerId: String) {
    val event = ReleaseDateChangedEvent(additionalInformation = (CalculateReleaseDatesAdditionalInformation(prisonerId)))
    domainTopic.snsClient.publish(
      PublishRequest.builder()
        .topicArn(domainTopic.arn)
        .message(mapper.writeValueAsString(event))
        .messageAttributes(mapOf("eventType" to MessageAttributeValue.builder().dataType("String").stringValue(event.eventType).build()))
        .build(),
    )
    log.info("Published 'release dates changed event' for: $prisonerId")
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
