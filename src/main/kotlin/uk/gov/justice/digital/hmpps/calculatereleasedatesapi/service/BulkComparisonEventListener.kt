package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(value = ["bulk.calculation.process"], havingValue = "SQS", matchIfMissing = true)
class BulkComparisonEventListener(
  private val objectMapper: ObjectMapper,
  private val bulkComparisonEventService: BulkComparisonEventHandlerService,
) {

  private val tracer = GlobalOpenTelemetry.getTracer("uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service")

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(
    "bulkcomparison",
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = $$"${bulk.calculation.concurrency}",
    maxMessagesPerPoll = $$"${bulk.calculation.concurrency}",
  )
  fun onMessage(
    rawMessage: String,
  ) {
    log.debug("Received message {}", rawMessage)
    val span = tracer.spanBuilder("BulkComparisonEventListener")
      .setSpanKind(SpanKind.CONSUMER)
      .setNoParent() // Force a new trace ID here to stop all calculations being processed under the same operation_id
      .startSpan()
    val scope = span.makeCurrent()
    try {
      val sqsMessage: SQSMessage = objectMapper.readValue(rawMessage)
      return when (sqsMessage.Type) {
        MESSAGE_TYPE -> {
          val message = objectMapper.readValue<InternalMessage<BulkComparisonMessageBody>>(sqsMessage.Message)
          bulkComparisonEventService.handleBulkComparisonMessage(message)
          bulkComparisonEventService.updateCountsAndCheckIfComparisonIsComplete(message)
        }
        else -> {}
      }
    } catch (t: Throwable) {
      span.recordException(t)
      throw t
    } finally {
      scope.close()
      span.end()
    }
  }
}
