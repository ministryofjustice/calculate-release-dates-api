package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation

class AdjustmentsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val adjustmentsApi = AdjustmentsApiMockServer()
    const val DEFAULT = "default"
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val jsonTransformation = JsonTransformation()

  override fun beforeAll(context: ExtensionContext) {
    adjustmentsApi.start()

    val adjustments = jsonTransformation.getAllAdjustments()
    val defaultAdjustment = adjustments[DEFAULT]!!

    val allPrisoners = jsonTransformation.getAllIntegrationPrisonerNames().distinct()

    allPrisoners.forEach {
      val adjustment = if (adjustments.containsKey(it)) {
        log.info("Stubbing adjustments prisonerId $it, from file $it")
        adjustments[it]!!
      } else {
        log.info("Stubbing adjustments prisonerId $it, from file $DEFAULT")
        defaultAdjustment
      }
      adjustmentsApi.stubGetAdjustments(it, adjustment)
    }
  }

  override fun beforeEach(context: ExtensionContext) {
    adjustmentsApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    adjustmentsApi.stop()
  }
}

class AdjustmentsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8334
  }

  fun stubGetAdjustments(prisonerId: String, json: String): StubMapping = stubFor(
    get(urlMatching("/adjustments.*$prisonerId.*"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(json)
          .withStatus(200),
      ),
  )
}
