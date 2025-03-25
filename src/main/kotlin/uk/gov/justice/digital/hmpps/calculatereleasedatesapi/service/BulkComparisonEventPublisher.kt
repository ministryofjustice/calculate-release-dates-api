package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.retry.support.RetryTemplate
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse
import uk.gov.justice.hmpps.sqs.DEFAULT_BACKOFF_POLICY
import uk.gov.justice.hmpps.sqs.DEFAULT_RETRY_POLICY
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.util.UUID

const val MESSAGE_TYPE = "calculate_release_dates_bulk_comparison_request"

@Service
@ConditionalOnProperty(value = ["bulk.calculation.process"], havingValue = "SQS", matchIfMissing = true)
class BulkComparisonEventPublisher(
  private val hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
) {

  fun sendMessageBatch(comparisonId: Long, persons: List<String>, username: String, establishment: String?) {
    val queue = hmppsQueueService.findByQueueId("bulkcomparison")
      ?: throw IllegalStateException("Queue not found for bulkcomparison")

    persons.chunked(10).forEach { chunk ->
      val publishRequest =
        SendMessageBatchRequest
          .builder()
          .queueUrl(queue.queueUrl)
          .entries(
            chunk.map {
              val sqsMessage = SQSMessage(
                Type = MESSAGE_TYPE,
                Message = InternalMessage(
                  BulkComparisonMessageBody(
                    comparisonId = comparisonId,
                    personId = it,
                    establishment = establishment,
                    username = username,
                  ),
                ).toJson(),
              )
              SendMessageBatchRequestEntry
                .builder()
                .id(UUID.randomUUID().toString())
                .messageBody(objectMapper.writeValueAsString(sqsMessage))
                .build()
            },
          ).build()
      val retryTemplate =
        RetryTemplate().apply {
          setRetryPolicy(DEFAULT_RETRY_POLICY)
          setBackOffPolicy(DEFAULT_BACKOFF_POLICY)
        }
      retryTemplate.execute<SendMessageBatchResponse, RuntimeException> {
        queue.sqsClient.sendMessageBatch(publishRequest).get()
      }
    }
  }

  private fun Any.toJson() = objectMapper.writeValueAsString(this)
}

data class InternalMessage<T>(
  val body: T,
)

data class BulkComparisonMessageBody(
  val comparisonId: Long,
  val personId: String,
  val username: String,
  val establishment: String?,
)

@JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SQSMessage(val Type: String, val Message: String, val MessageId: String? = null, val MessageAttributes: MessageAttributes? = null)
data class MessageAttributes(val eventType: EventType)
data class EventType(val Value: String, val Type: String)
