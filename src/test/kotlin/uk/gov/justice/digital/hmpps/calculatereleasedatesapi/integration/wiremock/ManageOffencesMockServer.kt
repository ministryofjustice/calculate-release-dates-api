package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.PcscMarkers

class ManageOffencesApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback,
  ParameterResolver {
  companion object {
    @JvmField
    val manageOffencesApi = ManageOffencesMockServer()
  }

  private val objectMapper = TestUtil.objectMapper()

  override fun beforeAll(context: ExtensionContext) {
    manageOffencesApi.start()
    manageOffencesApi.stubMOResponse()
    manageOffencesApi.stubMOMultipleResults()
    manageOffencesApi.stubSX03014MOResponse()
    manageOffencesApi.stub500Response()
    manageOffencesApi.stubBaseResponse()
    manageOffencesApi.stubSdsExclusionsDefaultToNo()
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

  override fun resolveParameter(parameterContext: ParameterContext, context: ExtensionContext): Any = MockManageOffencesClient(manageOffencesApi, objectMapper)
}

class ManageOffencesMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8335
  }

  fun stubMOResponse(): StubMapping = stubFor(
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

  fun stubSX03014MOResponse(): StubMapping = stubFor(
    get(urlMatching("/schedule/pcsc-indicators\\?offenceCodes=(SX03014|SX03013A)"))
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
                    }
                }
            ]
            """.trimIndent(),
          )
          .withStatus(200)
          .withTransformers("response-template"),
      ),
  )

  fun stubMOMultipleResults(): StubMapping = stubFor(
    get(urlMatching("/schedule/pcsc-indicators\\?offenceCodes=COML022,COML025A"))
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

  fun stubBaseResponse(): StubMapping = stubFor(
    get(urlMatching("/schedule/pcsc-indicators\\?offenceCodes=([A-Za-z0-9]+)"))
      .atPriority(Int.MAX_VALUE)
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """[
                {
                    "offenceCode": "{{request.query.offenceCodes}}",
                    "pcscMarkers": {
                        "inListA": false,
                        "inListB": false,
                        "inListC": false,
                        "inListD": false
                    }
                }
            ]
            """.trimIndent(),
          )
          .withStatus(200)
          .withTransformers("response-template"),
      ),
  )
  fun stubSpecificResponse(offences: String, json: String): StubMapping = stubFor(
    get(urlMatching("/schedule/pcsc-indicators\\?offenceCodes=$offences"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(json)
          .withStatus(200),
      ),
  )

  fun stub500Response(): StubMapping = stubFor(
    get(urlMatching("/schedule/pcsc-indicators\\?offenceCodes=500Response"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(500),
      ),
  )

  fun stubSdsExclusionsDefaultToNo(): StubMapping = stubFor(
    get(urlMatching("/schedule/sds-early-release-exclusions\\?offenceCodes=([A-Za-z0-9,]+)"))
      .atPriority(Int.MAX_VALUE)
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """[
                {{#each request.query.offenceCodes as |offenceCode|}}
                {
                    "offenceCode": "{{{offenceCode}}}",
                    "schedulePart": "NONE"
                }{{#if @last}}{{else}},{{/if}}
                {{/each}}
            ]
            """.trimIndent(),
          )
          .withStatus(200)
          .withTransformers("response-template"),
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
  private val objectMapper: ObjectMapper,
) {

  fun withPCSCMarkersResponse(vararg moResponseToMock: OffencePcscMarkers, offences: String): MockManageOffencesClient {
    manageOffencesApi.stubSpecificResponse(offences, objectMapper.writeValueAsString(moResponseToMock.toList()))
    return this
  }
  fun withStub(mappingBuilder: MappingBuilder): StubMapping = manageOffencesApi.stubFor(mappingBuilder)
  fun noneInPCSC(offences: List<String>): MockManageOffencesClient {
    val defaultMarkers = offences.map { offenceCode ->
      OffencePcscMarkers(
        offenceCode = offenceCode,
        pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = false, inListD = false),
      )
    }
    manageOffencesApi.stubSpecificResponse(
      offences.joinToString(","),
      objectMapper.writeValueAsString(defaultMarkers),
    )
    return this
  }
  fun stubToreraOffenceCodes(offenceCodes: List<String>) {
    manageOffencesApi.stubToreraOffenceCodes(offenceCodes)
  }
}
