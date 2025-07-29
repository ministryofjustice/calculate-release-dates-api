package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(value = ["bulk.calculation.process"], havingValue = "SQS", matchIfMissing = true)
class BulkComparisonEventListener(
  private val objectMapper: ObjectMapper,
  private val bulkComparisonEventService: BulkComparisonEventService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(
    "bulkcomparison",
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "6",
    maxMessagesPerPoll = "6",
  )
  fun onMessage(
    rawMessage: String,
  ) {
    log.debug("Received message {}", rawMessage)
    val sqsMessage: SQSMessage = objectMapper.readValue(rawMessage)
    return when (sqsMessage.Type) {
      MESSAGE_TYPE -> {
        val message = objectMapper.readValue<InternalMessage<BulkComparisonMessageBody>>(sqsMessage.Message)
        bulkComparisonEventService.handleBulkComparisonMessage(message)
        bulkComparisonEventService.updateCountsAndCheckIfComparisonIsComplete(message)
      } else -> {}
    }
  }
}
