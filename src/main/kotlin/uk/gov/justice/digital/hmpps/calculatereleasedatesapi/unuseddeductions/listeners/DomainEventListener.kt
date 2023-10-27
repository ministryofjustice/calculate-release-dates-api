package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service.UnusedDeductionsService
import java.util.concurrent.CompletableFuture

const val RETRY_CREATE_MAPPING = "RETRY_CREATE_MAPPING"

@Service
class DomainEventListener(
  internal val service: CreateMappingRetryable,
  internal val objectMapper: ObjectMapper,
  internal val telemetryClient: TelemetryClient,
  internal val unusedDeductionsService: UnusedDeductionsService
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("sentencing", factory = "hmppsQueueContainerFactoryProxy")
  fun onDomainEvent(
    rawMessage: String
  ): CompletableFuture<Void> {
    log.debug("Received message {}", rawMessage)
    val sqsMessage: SQSMessage = objectMapper.readValue(rawMessage)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val (eventType) = objectMapper.readValue<HMPPSDomainEvent>(sqsMessage.Message)
          when (eventType) {
            "release-date-adjustments.adjustment.inserted",
            "release-date-adjustments.adjustment.updated",
            "release-date-adjustments.adjustment.deleted" -> unusedDeductionsService.handleMessage(objectMapper.readValue(sqsMessage.Message))
          }
        }

        RETRY_CREATE_MAPPING -> runCatching { service.retryCreateMapping(sqsMessage.Message) }
          .onFailure {
            telemetryClient.trackEvent(
              "create-mapping-retry-failure",
              mapOf("retryMessage" to sqsMessage.Message),
              null,
            )
            throw it
          }
      }
    }
  }

}

private fun asCompletableFuture(
  process: suspend () -> Unit,
): CompletableFuture<Void> {
  return CoroutineScope(Dispatchers.Default).future {
    process()
  }.thenAccept { }
}

interface CreateMappingRetryable {
  suspend fun retryCreateMapping(message: String)
}
