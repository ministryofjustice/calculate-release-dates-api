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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.MaxRetryAchievedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.OffenceSdsExclusion

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
          OffenceSdsExclusion("SuccessExample1", OffenceSdsExclusion.SchedulePart.NONE),
          OffenceSdsExclusion("SuccessExample2", OffenceSdsExclusion.SchedulePart.SEXUAL),
          OffenceSdsExclusion("SuccessExample3", OffenceSdsExclusion.SchedulePart.VIOLENT),
          OffenceSdsExclusion("SuccessExample4", OffenceSdsExclusion.SchedulePart.DOMESTIC_ABUSE),
          OffenceSdsExclusion("SuccessExample5", OffenceSdsExclusion.SchedulePart.NATIONAL_SECURITY),
          OffenceSdsExclusion("SuccessExample6", OffenceSdsExclusion.SchedulePart.TERRORISM),
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
        OffenceSdsExclusion("RetryExample1", OffenceSdsExclusion.SchedulePart.NONE),
        OffenceSdsExclusion("RetryExample2", OffenceSdsExclusion.SchedulePart.SEXUAL),
        OffenceSdsExclusion("RetryExample3", OffenceSdsExclusion.SchedulePart.VIOLENT),
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
      .isInstanceOf(MaxRetryAchievedException::class.java)
      .hasMessageContaining("Max retries - getSdsExclusionsForOffenceCodes")
  }
}
