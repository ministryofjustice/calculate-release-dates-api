package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.prisonapi.model.CalculableSentenceEnvelopeVersion2
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.eventTypeMessageAttributes

const val MESSAGE_TYPE = "calculate_release_dates_bulk_comparison_request"

@Service
class BulkComparisonEventPublisher(
  private val hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
) {

  fun sendMessage(comparisonId: Long, person: CalculableSentenceEnvelopeVersion2, totalToCompare: Int, username: String, establishment: String?) {
    val queue = hmppsQueueService.findByQueueId("bulkcomparison")
      ?: throw IllegalStateException("Queue not found for bulkcomparison")
    val sqsMessage = SQSMessage(
      Type = MESSAGE_TYPE,
      Message = InternalMessage(
        BulkComparisonMessageBody(
          comparisonId = comparisonId,
          personId = person.prisonerNumber,
          bookingId = person.bookingId,
          totalToCompare = totalToCompare,
          establishment = establishment,
          username = username,
        ),
      ).toJson(),
    )

    queue.sqsClient.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(queue.queueUrl)
        .messageBody(sqsMessage.toJson())
        .eventTypeMessageAttributes(MESSAGE_TYPE)
        .build(),
    )
  }

  private fun Any.toJson() = objectMapper.writeValueAsString(this)
}

data class InternalMessage<T>(
  val body: T,
)

data class BulkComparisonMessageBody(
  val comparisonId: Long,
  val personId: String,
  val bookingId: Long,
  val totalToCompare: Int,
  val username: String,
  val establishment: String?,
)

@JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SQSMessage(val Type: String, val Message: String, val MessageId: String? = null, val MessageAttributes: MessageAttributes? = null)
data class MessageAttributes(val eventType: EventType)
data class EventType(val Value: String, val Type: String)
