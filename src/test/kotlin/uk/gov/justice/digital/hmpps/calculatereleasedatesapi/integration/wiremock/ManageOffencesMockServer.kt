package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation

/*
    This class mocks the manage offences api.
    JSON files exist in 'src/test/resources/test_data/api_integration
    Add files to any sub-directory to automatically stub rest calls. The file name will act as the prisoner id.
    The hashcode of the prisoner id string, will be the booking id.
    Once a file is added to any of the directories, all calls will be stubbed, if not all directories have an
    entry for the given prisoner id, the mock will fallback to a default json file.
 */
class ManageOffensesApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val manageOffensesApi = ManageOffensesMockServer();
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  override fun beforeAll(context: ExtensionContext) {
    manageOffensesApi.start()
    manageOffensesApi.stubMOResponse()
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
    private const val WIREMOCK_PORT = 8334
  }

  fun stubMOResponse(): StubMapping =
    stubFor(
      get("/api/manage")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """""".trimIndent(),
            )
            .withStatus(200),
        ),
    )
}

