package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerDetails

class PrisonApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val prisonApi = PrisonApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    prisonApi.start()
    prisonApi.stubGetPrisonerDetails("A1234AA")
  }

  override fun beforeEach(context: ExtensionContext) {
    prisonApi.resetRequests()
    prisonApi.stubGetPrisonerDetails("A1234AA")
  }

  override fun afterAll(context: ExtensionContext) {
    prisonApi.stop()
  }
}

class PrisonApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8332
  }

  var moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
  var jsonAdapter: JsonAdapter<PrisonerDetails> = moshi.adapter<PrisonerDetails>(PrisonerDetails::class.java)

  private val prisonerDetails = PrisonerDetails(1L, "A1234AA")

  fun stubGetPrisonerDetails(prisonerId: String): StubMapping =
    stubFor(
      get("/api/offenders/$prisonerId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(jsonAdapter.toJson(prisonerDetails))
            .withStatus(200)
        )
    )
}
