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

class RemandAndSentencingApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val remandAndSentencingApi = RemandAndSentencingApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    remandAndSentencingApi.start()
  }

  override fun afterAll(context: ExtensionContext) {
    remandAndSentencingApi.stop()
  }

  override fun beforeEach(context: ExtensionContext) {
    remandAndSentencingApi.resetRequests()
    remandAndSentencingApi.stubGetSentenceNotFound()
  }
}

class RemandAndSentencingApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8338
  }

  fun stubGetSentence(sentenceUuid: String, chargeNumber: String): StubMapping = stubFor(
    get(urlMatching("/sentence/$sentenceUuid"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
                "sentenceUuid": "$sentenceUuid",
                "chargeNumber": "$chargeNumber",
                "periodLengths": [],
                "sentenceServeType": "CONCURRENT",
                "hasRecall": false
            }
            """.trimIndent(),
          )
          .withStatus(200),
      ),
  )

  fun stubGetSentenceNotFound(): StubMapping = stubFor(
    get(urlMatching("/sentence/.*"))
      .atPriority(Int.MAX_VALUE)
      .willReturn(aResponse().withStatus(404)),
  )
}
