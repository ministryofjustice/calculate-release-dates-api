package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock

import com.fasterxml.jackson.core.type.TypeReference
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil.Companion.objectMapper
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
  private val objectMapper = objectMapper()

  override fun beforeAll(context: ExtensionContext) {
    adjustmentsApi.start()

    val adjustmentApiAdjustments = jsonTransformation.getAllAdjustments()
    val allPrisoners = jsonTransformation.getAllIntegrationPrisonerNames().distinct()

    val defaultAdjustment = adjustmentApiAdjustments[DEFAULT]!!
    val defaultAsObject = objectMapper.readValue(defaultAdjustment, object : TypeReference<List<AdjustmentDto>>() {})

    allPrisoners.forEach { prisoner ->
      val bookingId = prisoner.hashCode().toLong()
      val adjustment = if (adjustmentApiAdjustments.containsKey(prisoner)) {
        log.info("Stubbing adjustments prisonerId $prisoner, from file $prisoner")
        val json = adjustmentApiAdjustments[prisoner]!!
        val jsonAsObject = objectMapper.readValue(json, object : TypeReference<List<AdjustmentDto>>() {})
        objectMapper.writeValueAsString(jsonAsObject.map { it.copy(person = prisoner, bookingId = bookingId) })
      } else {
        log.info("Stubbing adjustments prisonerId $prisoner, from file $DEFAULT")
        objectMapper.writeValueAsString(defaultAsObject.map { it.copy(person = prisoner, bookingId = bookingId) })
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
    get(urlPathEqualTo("/adjustments"))
      .withQueryParam("person", equalTo(prisonerId))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(json)
          .withStatus(200),
      ),
  )
}
