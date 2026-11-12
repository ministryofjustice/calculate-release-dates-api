@file:Suppress("ProtectedInFinal")

package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["crds.listeners.process=SQS", "crds.listeners.concurrency=5"])
@ActiveProfiles("sqs-test")
class SqsIntegrationTestBase : IntegrationTestBase() {

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  @Autowired
  private lateinit var testCrdsDomainEventListener: TestCrdsDomainEventListener

  @MockitoSpyBean
  private lateinit var hmppsSqsPropertiesSpy: HmppsSqsProperties

  private val bulkComparisonQueue by lazy { hmppsQueueService.findByQueueId("bulkcomparison") as HmppsQueue }

  private val prisonerEventsQueue by lazy { hmppsQueueService.findByQueueId("prisonerevents") as HmppsQueue }

  // this is a test-only subscription to see if CRDS events were published to HMPPS domain events topic
  private val hmppsEventsQueue by lazy { hmppsQueueService.findByQueueId("hmppseventqueue") as HmppsQueue }

  @BeforeEach
  fun cleanQueues() {
    await untilCallTo {
      bulkComparisonQueue.sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(bulkComparisonQueue.queueUrl).build())
      prisonerEventsQueue.sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(prisonerEventsQueue.queueUrl).build())
      hmppsEventsQueue.sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(hmppsEventsQueue.queueUrl).build())
      listOf(
        bulkComparisonQueue.sqsClient.countMessagesOnQueue(bulkComparisonQueue.queueUrl).get(),
        prisonerEventsQueue.sqsClient.countMessagesOnQueue(bulkComparisonQueue.queueUrl).get(),
        hmppsEventsQueue.sqsClient.countMessagesOnQueue(hmppsEventsQueue.queueUrl).get(),
      )
    } matches { it == listOf(0, 0, 0) }
    testCrdsDomainEventListener.resetReceivedEvents()
  }

  fun waitForAllCalculationEventsToBePublished(vararg expectedPrisonerIds: String) {
    await untilAsserted {
      assertThat(testCrdsDomainEventListener.getReceivedEvents()).containsExactlyInAnyOrder(*expectedPrisonerIds)
    }
  }

  @Suppress("SameParameterValue")
  protected fun publishBookingMoveEvent(bookingId: Long, from: String, to: String) {
    prisonerEventsQueue.sqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(prisonerEventsQueue.queueUrl).messageBody(
        bookingMovedDomainEvent(bookingId = bookingId, movedFromNomsNumber = from, movedToNomsNumber = to),
      ).build(),
    ).get()
  }

  @Suppress("SameParameterValue")
  protected fun publishPrisonerMergeEvent(retainedPrisoner: String, removedNomsNumber: String) {
    prisonerEventsQueue.sqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(prisonerEventsQueue.queueUrl).messageBody(
        prisonerMergedEvent(nomsNumber = retainedPrisoner, removedNomsNumber = removedNomsNumber),
      ).build(),
    ).get()
  }

  private fun bookingMovedDomainEvent(
    bookingId: Long,
    movedToNomsNumber: String,
    movedFromNomsNumber: String,
    bookingStartDateTime: String = "2020-01-01T00:00:00.000",
    eventType: String = "prison-offender-events.prisoner.booking.moved",
  ) = //language=JSON
    """{
    "MessageId": "${UUID.randomUUID()}", "Type": "Notification", "Timestamp": "${LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)}", 
    "Message": "{\"eventType\":\"$eventType\", \"description\": \"some desc\", \"additionalInformation\": {\"movedToNomsNumber\":\"$movedToNomsNumber\", \"movedFromNomsNumber\":\"$movedFromNomsNumber\", \"bookingId\":\"$bookingId\", \"bookingStartDateTime\":\"$bookingStartDateTime\"}}",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "${UUID.randomUUID()}"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "${System.currentTimeMillis()}"}
    }
}
    """.trimIndent()
  private fun prisonerMergedEvent(
    nomsNumber: String,
    removedNomsNumber: String,
    eventType: String = "prison-offender-events.prisoner.merged",
  ) = //language=JSON
    """{
    "MessageId": "${UUID.randomUUID()}", "Type": "Notification", "Timestamp": "${LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)}", 
    "Message": "{\"eventType\":\"$eventType\", \"description\": \"some desc\", \"additionalInformation\": {\"nomsNumber\":\"$nomsNumber\", \"removedNomsNumber\":\"$removedNomsNumber\"}}",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "${UUID.randomUUID()}"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "${System.currentTimeMillis()}"}
    }
}
    """.trimIndent()
}
