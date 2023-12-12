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

class ManageOffensesApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val manageOffensesApi = ManageOffensesMockServer()
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override fun beforeAll(context: ExtensionContext) {
    manageOffensesApi.start()
    manageOffensesApi.stubMOResponse()
    manageOffensesApi.stubMOMultipleResults()
  }

  override fun afterAll(context: ExtensionContext?) {
    manageOffensesApi.stop()
  }

  override fun beforeEach(context: ExtensionContext?) {
    manageOffensesApi.resetRequests()
  }
}

class ManageOffensesMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8335
  }

  fun stubMOResponse(): StubMapping =
    stubFor(
      get(urlMatching("/schedule/pcsc-indicators\\?offenceCodes=COML025A"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """[
                {
                    "offenceCode": "COML025A",
                    "pcscMarkers": {
                        "inListA": false,
                        "inListB": true,
                        "inListC": true,
                        "inListD": false
                    }
                }
            ]
              """.trimIndent(),
            )
            .withStatus(200),
        ),
    )

  fun stubMOMultipleResults(): StubMapping =
    stubFor(
      get(urlMatching("/schedule/pcsc-indicators\\?offenceCodes=COML025A,COML022"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """[
                {
                    "offenceCode": "COML025A",
                    "pcscMarkers": {
                        "inListA": false,
                        "inListB": true,
                        "inListC": true,
                        "inListD": false
                    }
                },
                {
                    "offenceCode": "COML022",
                    "pcscMarkers": {
                        "inListA": false,
                        "inListB": true,
                        "inListC": true,
                        "inListD": false
                    }
                }
            ]
              """.trimIndent(),
            )
            .withStatus(200),
        ),
    )
}
