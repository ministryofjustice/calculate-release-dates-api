package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.UserContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.SDSEarlyReleaseExclusionForOffenceCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.SDSEarlyReleaseExclusionSchedulePart

class ManageOffencesApiClientIntTest(private val mockManageOffencesClient: MockManageOffencesClient) : IntegrationTestBase() {

  @Autowired
  lateinit var manageOffencesApiClient: ManageOffencesApiClient

  @BeforeEach
  fun setUp() {
    val headers = HttpHeaders()
    setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR"))(headers)
    UserContext.setAuthToken(headers[HttpHeaders.AUTHORIZATION]?.firstOrNull())
  }

  @Test
  fun `should get sexual or violent for multiple offence codes`() {
    mockManageOffencesClient.withStub(
      get(urlMatching("/schedule/sds-early-release-exclusions\\?offenceCodes=SuccessExample1,SuccessExample2,SuccessExample3,SuccessExample4,SuccessExample5,SuccessExample6"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """[
                {
                    "offenceCode": "SuccessExample1",
                    "schedulePart": "NONE"
                },
                {
                    "offenceCode": "SuccessExample2",
                    "schedulePart": "SEXUAL"
                },
                {
                    "offenceCode": "SuccessExample3",
                    "schedulePart": "VIOLENT"
                },
                {
                    "offenceCode": "SuccessExample4",
                    "schedulePart": "DOMESTIC_ABUSE"
                },
                {
                    "offenceCode": "SuccessExample5",
                    "schedulePart": "NATIONAL_SECURITY"
                },
                {
                    "offenceCode": "SuccessExample6",
                    "schedulePart": "TERRORISM"
                }
            ]
              """.trimIndent(),
            )
            .withStatus(200)
            .withTransformers("response-template"),
        ),
    )
    assertThat(
      manageOffencesApiClient.getSdsExclusionsForOffenceCodes(
        listOf(
          "SuccessExample1",
          "SuccessExample2",
          "SuccessExample3",
          "SuccessExample4",
          "SuccessExample5",
          "SuccessExample6",
        ),
      ),
    )
      .isEqualTo(
        listOf(
          SDSEarlyReleaseExclusionForOffenceCode("SuccessExample1", SDSEarlyReleaseExclusionSchedulePart.NONE),
          SDSEarlyReleaseExclusionForOffenceCode("SuccessExample2", SDSEarlyReleaseExclusionSchedulePart.SEXUAL),
          SDSEarlyReleaseExclusionForOffenceCode("SuccessExample3", SDSEarlyReleaseExclusionSchedulePart.VIOLENT),
          SDSEarlyReleaseExclusionForOffenceCode("SuccessExample4", SDSEarlyReleaseExclusionSchedulePart.DOMESTIC_ABUSE),
          SDSEarlyReleaseExclusionForOffenceCode("SuccessExample5", SDSEarlyReleaseExclusionSchedulePart.NATIONAL_SECURITY),
          SDSEarlyReleaseExclusionForOffenceCode("SuccessExample6", SDSEarlyReleaseExclusionSchedulePart.TERRORISM),
        ),
      )
  }

  @Test
  fun `should retry and eventually get sexual or violent for multiple offence codes`() {
    mockManageOffencesClient.withStub(
      get(urlMatching("/schedule/sds-early-release-exclusions\\?offenceCodes=RetryExample1,RetryExample2,RetryExample3"))
        .inScenario("Retry Scenario")
        .whenScenarioStateIs("Started")
        .willReturn(
          aResponse()
            .withStatus(500),
        )
        .willSetStateTo("Second Attempt"),
    )

    mockManageOffencesClient.withStub(
      get(urlMatching("/schedule/sds-early-release-exclusions\\?offenceCodes=RetryExample1,RetryExample2,RetryExample3"))
        .inScenario("Retry Scenario")
        .whenScenarioStateIs("Second Attempt")
        .willReturn(
          aResponse()
            .withStatus(500),
        )
        .willSetStateTo("Third Attempt"),
    )

    mockManageOffencesClient.withStub(
      get(urlMatching("/schedule/sds-early-release-exclusions\\?offenceCodes=RetryExample1,RetryExample2,RetryExample3"))
        .inScenario("Retry Scenario")
        .whenScenarioStateIs("Third Attempt")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """[
                {
                    "offenceCode": "RetryExample1",
                    "schedulePart": "NONE"
                },
                {
                    "offenceCode": "RetryExample2",
                    "schedulePart": "SEXUAL"
                },
                {
                    "offenceCode": "RetryExample3",
                    "schedulePart": "VIOLENT"
                }
              ]
              """.trimIndent(),
            )
            .withStatus(200),
        ),
    )

    val result = manageOffencesApiClient.getSdsExclusionsForOffenceCodes(listOf("RetryExample1", "RetryExample2", "RetryExample3"))

    assertThat(result).isEqualTo(
      listOf(
        SDSEarlyReleaseExclusionForOffenceCode("RetryExample1", SDSEarlyReleaseExclusionSchedulePart.NONE),
        SDSEarlyReleaseExclusionForOffenceCode("RetryExample2", SDSEarlyReleaseExclusionSchedulePart.SEXUAL),
        SDSEarlyReleaseExclusionForOffenceCode("RetryExample3", SDSEarlyReleaseExclusionSchedulePart.VIOLENT),
      ),
    )
  }

  @Test
  fun `should throw exception when maximum retries are exceeded`() {
    mockManageOffencesClient.withStub(
      get(urlMatching("/schedule/sds-early-release-exclusions\\?offenceCodes=ErrorExample1,ErrorExample2,ErrorExample3"))
        .willReturn(
          aResponse()
            .withStatus(500),
        ),
    )

    assertThatThrownBy {
      manageOffencesApiClient.getSdsExclusionsForOffenceCodes(listOf("ErrorExample1", "ErrorExample2", "ErrorExample3"))
    }
      .isInstanceOf(ManageOffencesApiClient.MaxRetryAchievedException::class.java)
      .hasMessageContaining("getSdsExclusionsForOffenceCodes: Max retries - lookup failed")
  }
}
