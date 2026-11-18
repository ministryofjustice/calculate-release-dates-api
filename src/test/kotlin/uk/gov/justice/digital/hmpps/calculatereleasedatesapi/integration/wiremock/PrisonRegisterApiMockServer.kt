package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class PrisonRegisterApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val prisonRegisterApi = PrisonRegisterApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    prisonRegisterApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    prisonRegisterApi.resetAll()
    prisonRegisterApi.stubDefaults()
  }

  override fun afterAll(context: ExtensionContext) {
    prisonRegisterApi.stop()
  }
}

class PrisonRegisterApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8338
  }

  fun stubDefaults() {
    stubUnknownPrisons()
    stubActivePrisons("""[${prisonJson("PRIS", true)}, ${prisonJson("BXI", true)}]""")
    stubPrison("PRIS", active = true)
    stubPrison("DAI", active = false)
  }

  fun stubActivePrisons(json: String): StubMapping = stubFor(
    get(urlEqualTo("/prisons/search?active=true"))
      .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(json).withStatus(200)),
  )

  fun stubActivePrisonsUnavailable(): StubMapping = stubFor(
    get(urlEqualTo("/prisons/search?active=true")).willReturn(aResponse().withStatus(503)),
  )

  fun stubPrison(prisonId: String, active: Boolean): StubMapping = stubFor(
    get(urlEqualTo("/prisons/id/$prisonId"))
      .atPriority(1)
      .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(prisonJson(prisonId, active)).withStatus(200)),
  )

  private fun stubUnknownPrisons(): StubMapping = stubFor(
    get(urlPathMatching("/prisons/id/[^/]+"))
      .atPriority(10)
      .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""{"status":404,"userMessage":"Prison not found"}""").withStatus(404)),
  )

  private fun prisonJson(prisonId: String, active: Boolean) = """{"prisonId":"$prisonId","prisonName":"$prisonId prison","active":$active,"male":true,"female":false,"contracted":false,"lthse":false,"types":[],"categories":[],"addresses":[],"operators":[]}"""
}
