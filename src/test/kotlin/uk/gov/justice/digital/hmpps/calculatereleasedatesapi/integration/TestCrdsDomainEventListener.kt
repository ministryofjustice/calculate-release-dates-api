package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.event.ReleaseDateChangedEvent
import uk.gov.justice.hmpps.sqs.SnsMessage

@Service
@ConditionalOnProperty(value = ["crds.listeners.process"], havingValue = "SQS", matchIfMissing = true)
class TestCrdsDomainEventListener(private val objectMapper: ObjectMapper) {
  private val receivedEvents: MutableList<String> = mutableListOf()

  @SqsListener("hmppseventqueue", factory = "hmppsQueueContainerFactoryProxy")
  fun processMessage(message: SnsMessage) {
    val event = objectMapper.readValue<ReleaseDateChangedEvent>(message.message)
    receivedEvents.add(event.personReference?.findNomsIdentifier()!!)
  }

  fun resetReceivedEvents() {
    receivedEvents.clear()
  }

  fun getReceivedEvents(): List<String> = receivedEvents.toList()
}
