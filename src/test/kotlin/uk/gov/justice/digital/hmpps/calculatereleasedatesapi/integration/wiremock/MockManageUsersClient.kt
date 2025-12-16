package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageusers.UserDetails

class ManageUsersApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback,
  ParameterResolver {
  companion object {
    @JvmField
    val manageUsersApi = ManageUsersMockServer()
  }

  private val objectMapper = TestUtil.objectMapper()

  override fun beforeAll(context: ExtensionContext) {
    manageUsersApi.start()
  }

  override fun afterAll(context: ExtensionContext?) {
    manageUsersApi.stop()
  }

  override fun beforeEach(context: ExtensionContext?) {
    manageUsersApi.resetRequests()
  }

  override fun supportsParameter(parameterContext: ParameterContext, context: ExtensionContext): Boolean = parameterContext.parameter.type == MockManageUsersClient::class.java

  override fun resolveParameter(parameterContext: ParameterContext, context: ExtensionContext): Any = MockManageUsersClient(manageUsersApi, objectMapper)
}

class MockManageUsersClient(private val manageUsersApi: ManageUsersMockServer, private val objectMapper: ObjectMapper) {

  fun withUser(user: UserDetails): MockManageUsersClient {
    manageUsersApi.stubFor(
      get(urlMatching("/users/${user.username}"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(user))
            .withStatus(200),
        ),
    )
    return this
  }
  fun withNotFoundUser(username: String): MockManageUsersClient {
    manageUsersApi.stubFor(
      get(urlMatching("/users/$username"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(404),
        ),
    )
    return this
  }
}

class ManageUsersMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8337
  }
}
