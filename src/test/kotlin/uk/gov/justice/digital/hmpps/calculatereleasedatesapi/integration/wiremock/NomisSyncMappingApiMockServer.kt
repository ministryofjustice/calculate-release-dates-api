package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock

import com.fasterxml.jackson.core.type.TypeReference
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2
import com.github.tomakehurst.wiremock.http.ResponseDefinition
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.nomissyncmapping.model.NomisDpsSentenceMapping
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.nomissyncmapping.model.NomisSentenceId
import java.util.UUID

class NomisSyncMappingApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val nomisSyncMappingApiMockServer = NomisSyncMappingApiMockServer(
      wireMockConfig().extensions(NomisSyncMappingApiBodyTransformer::class.java),
    )
  }

  private val objectMapper = TestUtil.objectMapper()

  override fun beforeAll(context: ExtensionContext) {
    nomisSyncMappingApiMockServer.start()
    nomisSyncMappingApiMockServer.stubPostNomisMapping()
  }

  override fun afterAll(context: ExtensionContext) {
    nomisSyncMappingApiMockServer.stop()
  }

  override fun beforeEach(context: ExtensionContext) {
    nomisSyncMappingApiMockServer.resetRequests()
  }
}

class NomisSyncMappingApiMockServer(config: WireMockConfiguration) : WireMockServer(config.port(WIREMOCK_PORT)) {
  companion object {
    private const val WIREMOCK_PORT = 8336
  }

  fun stubPostNomisMapping(): StubMapping = stubFor(
    post(urlMatching("/api/sentences/nomis"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withTransformers("nomis-sync-mapping-body")
          .withStatus(200),
      ),
  )
}

class NomisSyncMappingApiBodyTransformer : ResponseDefinitionTransformerV2 {
  override fun transform(serveEvent: ServeEvent): ResponseDefinition {
    with(serveEvent) {
      val ids =
        TestUtil.objectMapper().readValue(request.bodyAsString, object : TypeReference<List<NomisSentenceId>>() {})
      val mappings = ids.map {
        NomisDpsSentenceMapping(
          nomisSentenceId = it,
          dpsSentenceId = UUID.nameUUIDFromBytes(("${it.nomisBookingId}-${it.nomisSentenceSequence}").toByteArray())
            .toString(),
        )
      }
      return ResponseDefinitionBuilder()
        .withHeaders(responseDefinition.headers)
        .withStatus(responseDefinition.status)
        .withBody(TestUtil.objectMapper().writeValueAsString(mappings))
        .build()
    }
  }

  override fun getName(): String = "nomis-sync-mapping-body"

  override fun applyGlobally(): Boolean = false
}
