package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class OAuthExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {

  companion object {
    @JvmField
    val oAuthApi = OAuthMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    oAuthApi.start()
    oAuthApi.stubGrantToken()
  }

  override fun beforeEach(context: ExtensionContext) {
    oAuthApi.resetRequests()
    oAuthApi.stubGrantToken()
  }

  override fun afterAll(context: ExtensionContext) {
    oAuthApi.stop()
  }
}

class OAuthMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8090
  }

  fun stubGrantToken() {
    stubFor(
      WireMock.post(WireMock.urlEqualTo("/auth/oauth/token"))
        .willReturn(
          aResponse()
            .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
            .withBody(
              """
              {
                "token_type": "bearer",
                "access_token": "atoken"
              }
              """.trimIndent()
            )
        )
    )
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/auth/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )
  }
}
