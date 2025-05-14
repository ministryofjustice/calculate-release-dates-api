package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import org.awaitility.core.ConditionFactory
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.Duration

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["bulk.calculation.process=sqs"])
@ActiveProfiles("sqs-test")
class SqsIntegrationTestBase : IntegrationTestBase() {
  protected val awaitAtMost30Secs: ConditionFactory get() = await.atMost(Duration.ofSeconds(30))

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  @MockitoSpyBean
  protected lateinit var hmppsSqsPropertiesSpy: HmppsSqsProperties

  protected val bulkComparisonQueue by lazy { hmppsQueueService.findByQueueId("bulkcomparison") as HmppsQueue }

  @BeforeEach
  fun cleanQueue() {
    await untilCallTo {
      bulkComparisonQueue.sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(bulkComparisonQueue.queueUrl).build())
      bulkComparisonQueue.sqsClient.countMessagesOnQueue(bulkComparisonQueue.queueUrl).get()
    } matches { it == 0 }
  }

  companion object {
    private val localStackContainer = LocalStackContainer.instance

    @Suppress("unused")
    @JvmStatic
    @DynamicPropertySource
    fun testcontainers(registry: DynamicPropertyRegistry) {
      localStackContainer?.also { LocalStackContainer.setLocalStackProperties(it, registry) }
    }
  }

  protected fun jsonString(any: Any) = objectMapper.writeValueAsString(any) as String

  fun getNumberOfMessagesCurrentlyOnQueue(): Int? = bulkComparisonQueue.sqsClient.countMessagesOnQueue(bulkComparisonQueue.queueUrl).get()

  fun getLatestMessage(): ReceiveMessageResponse? = bulkComparisonQueue.sqsClient.receiveMessage(ReceiveMessageRequest.builder().maxNumberOfMessages(2).queueUrl(bulkComparisonQueue.queueUrl).build()).get()
}
