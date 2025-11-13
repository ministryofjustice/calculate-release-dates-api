package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock

import com.fasterxml.jackson.core.type.TypeReference
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
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
    val defaultAsObject = TestUtil.objectMapper().readValue(defaultAdjustment, object : TypeReference<List<AdjustmentDto>>() {})

    val allPrisoners = jsonTransformation.getAllIntegrationPrisonerNames().distinct()

    allPrisoners.forEach { prisoner ->
      val adjustment = if (adjustments.containsKey(prisoner)) {
        log.info("Stubbing adjustments prisonerId $prisoner, from file $prisoner")
        val json = adjustments[prisoner]!!
        val jsonAsObject = TestUtil.objectMapper().readValue(json, object : TypeReference<List<AdjustmentDto>>() {})
        TestUtil.objectMapper().writeValueAsString(jsonAsObject.map { it.copy(person = prisoner, bookingId = prisoner.hashCode().toLong()) })
      } else {
        log.info("Stubbing adjustments prisonerId $prisoner, from file $DEFAULT")
        TestUtil.objectMapper().writeValueAsString(defaultAsObject.map { it.copy(person = prisoner, bookingId = prisoner.hashCode().toLong()) })
      }
      adjustmentsApi.stubGetAdjustments(prisoner, adjustment)
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
    get(urlMatching("/adjustments.*person=$prisonerId.*"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(json)
          .withStatus(200),
      ),
  )
}
