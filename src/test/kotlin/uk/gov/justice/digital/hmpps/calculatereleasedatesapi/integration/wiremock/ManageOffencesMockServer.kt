package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.PcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.SdsOffenceDetails

class ManageOffencesApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback,
  ParameterResolver {
  companion object {
    @JvmField
    val manageOffencesApi = ManageOffencesMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    manageOffencesApi.start()
    manageOffencesApi.stubSdsDetailsResponseForCOML025A()
    manageOffencesApi.stubSdsDetailsResponseForCOML022AndCOML025A()
    manageOffencesApi.stubSdsDetailsResponseForSX03014()
    manageOffencesApi.stubDefaultSdsOffenceDetailsResponse()
    manageOffencesApi.subOffencesFromCodes()
    manageOffencesApi.stubToreraOffenceCodes()
  }

  override fun afterAll(context: ExtensionContext) {
    manageOffencesApi.stop()
  }

  override fun beforeEach(context: ExtensionContext) {
    manageOffencesApi.resetRequests()
  }

  override fun supportsParameter(parameterContext: ParameterContext, context: ExtensionContext): Boolean = parameterContext.parameter.type == MockManageOffencesClient::class.java

  override fun resolveParameter(parameterContext: ParameterContext, context: ExtensionContext): Any = MockManageOffencesClient(manageOffencesApi)
}

class ManageOffencesMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8335
  }

  fun stubSdsDetailsResponseForCOML025A(): StubMapping = stubFor(
    get(urlMatching("/schedule/sds-offence-details\\?offenceCodes=COML025A"))
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
                    },
                    "earlyReleaseExclusions": []
                }
            ]
            """.trimIndent(),
          )
          .withStatus(200),
      ),
  )

  fun stubSdsDetailsResponseForSX03014(): StubMapping = stubFor(
    get(urlMatching("/schedule/sds-offence-details\\?offenceCodes=(SX03014|SX03013A)"))
      .atPriority(1)
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """[
                {
                    "offenceCode": "{{request.query.offenceCodes}}",
                    "pcscMarkers": {
                        "inListA": true,
                        "inListB": true,
                        "inListC": true,
                        "inListD": true
                    },
                    "earlyReleaseExclusions": []
                }
            ]
            """.trimIndent(),
          )
          .withStatus(200)
          .withTransformers("response-template"),
      ),
  )

  fun stubSdsDetailsResponseForCOML022AndCOML025A(): StubMapping = stubFor(
    get(urlMatching("/schedule/sds-offence-details\\?offenceCodes=COML022,COML025A"))
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
                    },
                    "earlyReleaseExclusions": []
                },
                {
                    "offenceCode": "COML022",
                    "pcscMarkers": {
                        "inListA": false,
                        "inListB": true,
                        "inListC": true,
                        "inListD": false
                    },
                    "earlyReleaseExclusions": []
                }
            ]
            """.trimIndent(),
          )
          .withStatus(200),
      ),
  )

  fun stubDefaultSdsOffenceDetailsResponse(): StubMapping = stubFor(
    get(urlMatching("/schedule/sds-offence-details\\?offenceCodes=([A-Za-z0-9,]+)"))
      .atPriority(Int.MAX_VALUE)
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """[
                {{#each request.query.offenceCodes as |offenceCode|}}
                {
                    "offenceCode": "{{{offenceCode}}}",
                    "pcscMarkers": {
                        "inListA": false,
                        "inListB": false,
                        "inListC": false,
                        "inListD": false
                    },
                    "earlyReleaseExclusions": []
                }{{#if @last}}{{else}},{{/if}}
                {{/each}}
            ]
            """.trimIndent(),
          )
          .withStatus(200)
          .withTransformers("response-template"),
      ),
  )

  fun stubSpecificSDSOffenceDetailsResponse(offences: String, response: List<SdsOffenceDetails>): StubMapping = stubFor(
    get(urlMatching("/schedule/sds-offence-details\\?offenceCodes=$offences"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(TestUtil.objectMapper().writeValueAsString(response))
          .withStatus(200),
      ),
  )

  fun subOffencesFromCodes(): StubMapping = stubFor(
    get(urlMatching("/offences/code/multiple\\?offenceCodes=([A-Za-z0-9,]+)"))
      .atPriority(Int.MAX_VALUE)
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            (
              """
              []
              """.trimIndent()
              ),
          )
          .withStatus(200)
          .withTransformers("response-template"),
      ),
  )

  fun stubToreraOffenceCodes(offenceCodes: List<String> = emptyList()): StubMapping = stubFor(
    get(urlMatching("/schedule/torera-offence-codes"))
      .atPriority(Int.MAX_VALUE)
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("[${offenceCodes.joinToString(",") { "\"$it\"" }}]".trimIndent())
          .withStatus(200)
          .withTransformers("response-template"),
      ),
  )
}

class MockManageOffencesClient(
  private val manageOffencesApi: ManageOffencesMockServer,
) {

  fun withSdsOffenceDetailsResponse(vararg moResponseToMock: SdsOffenceDetails, offences: String): MockManageOffencesClient {
    manageOffencesApi.stubSpecificSDSOffenceDetailsResponse(offences, moResponseToMock.toList())
    return this
  }

  fun withStub(mappingBuilder: MappingBuilder): StubMapping = manageOffencesApi.stubFor(mappingBuilder)
  fun notSDSPlusAndNoExclusions(offences: List<String>): MockManageOffencesClient {
    val offencesAsString = offences.joinToString(",")
    manageOffencesApi.stubSpecificSDSOffenceDetailsResponse(
      offencesAsString,
      offences.map { offenceCode ->
        SdsOffenceDetails(
          offenceCode = offenceCode,
          pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = false, inListD = false),
          earlyReleaseExclusions = emptyList(),
        )
      },
    )
    return this
  }

  fun stubToreraOffenceCodes(offenceCodes: List<String>) {
    manageOffencesApi.stubToreraOffenceCodes(offenceCodes)
  }
}
