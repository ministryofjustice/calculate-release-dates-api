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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.OffenceSdsExclusionIndicator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.PcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageoffencesapi.model.SdsOffenceDetails

class ManageOffencesApiClientIntTest(private val mockManageOffencesClient: MockManageOffencesClient) : IntegrationTestBase() {

  @Autowired
  lateinit var manageOffencesApiClient: ManageOffencesApiClient

  private val noPscsMarkers = PcscMarkers(inListA = false, inListB = false, inListC = false, inListD = false)
  private val somePscsMarkers = PcscMarkers(inListA = true, inListB = false, inListC = false, inListD = true)

  @BeforeEach
  fun setUp() {
    val headers = HttpHeaders()
    setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR"))(headers)
    UserContext.setAuthToken(headers[HttpHeaders.AUTHORIZATION]?.firstOrNull())
  }

  @Test
  fun `should get sexual or violent for multiple offence codes`() {
    mockManageOffencesClient.withStub(
      get(urlMatching("/schedule/sds-offence-details\\?offenceCodes=SuccessExample1,SuccessExample2,SuccessExample3,SuccessExample4,SuccessExample5,SuccessExample6,SuccessExample7"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """[
                {
                    "offenceCode": "SuccessExample1",
                    "pcscMarkers": {"inListA": false, "inListB": false, "inListC": false, "inListD": false},
                    "earlyReleaseExclusions": []
                },
                {
                    "offenceCode": "SuccessExample2",
                    "pcscMarkers": {"inListA": true, "inListB": false, "inListC": false, "inListD": true},
                    "earlyReleaseExclusions": ["SEXUAL"]
                },
                {
                    "offenceCode": "SuccessExample3",
                    "pcscMarkers": {"inListA": false, "inListB": false, "inListC": false, "inListD": false},
                    "earlyReleaseExclusions": ["VIOLENT"]
                },
                {
                    "offenceCode": "SuccessExample4",
                    "pcscMarkers": {"inListA": false, "inListB": false, "inListC": false, "inListD": false},
                    "earlyReleaseExclusions": ["DOMESTIC_ABUSE"]
                },
                {
                    "offenceCode": "SuccessExample5",
                    "pcscMarkers": {"inListA": false, "inListB": false, "inListC": false, "inListD": false},
                    "earlyReleaseExclusions": ["NATIONAL_SECURITY"]
                },
                {
                    "offenceCode": "SuccessExample6",
                    "pcscMarkers": {"inListA": false, "inListB": false, "inListC": false, "inListD": false},
                    "earlyReleaseExclusions": ["TERRORISM"]
                },
                {
                    "offenceCode": "SuccessExample7",
                    "pcscMarkers": {"inListA": true, "inListB": false, "inListC": false, "inListD": true},
                    "earlyReleaseExclusions": ["NATIONAL_SECURITY", "SCHEDULE_13_PART_3"]
                }
            ]
              """.trimIndent(),
            )
            .withStatus(200)
            .withTransformers("response-template"),
        ),
    )
    assertThat(
      manageOffencesApiClient.getSdsOffenceDetails(
        listOf(
          "SuccessExample1",
          "SuccessExample2",
          "SuccessExample3",
          "SuccessExample4",
          "SuccessExample5",
          "SuccessExample6",
          "SuccessExample7",
        ),
      ),
    )
      .isEqualTo(
        listOf(
          SdsOffenceDetails("SuccessExample1", noPscsMarkers, emptyList()),
          SdsOffenceDetails("SuccessExample2", somePscsMarkers, listOf(OffenceSdsExclusionIndicator.SEXUAL)),
          SdsOffenceDetails("SuccessExample3", noPscsMarkers, listOf(OffenceSdsExclusionIndicator.VIOLENT)),
          SdsOffenceDetails("SuccessExample4", noPscsMarkers, listOf(OffenceSdsExclusionIndicator.DOMESTIC_ABUSE)),
          SdsOffenceDetails("SuccessExample5", noPscsMarkers, listOf(OffenceSdsExclusionIndicator.NATIONAL_SECURITY)),
          SdsOffenceDetails("SuccessExample6", noPscsMarkers, listOf(OffenceSdsExclusionIndicator.TERRORISM)),
          SdsOffenceDetails("SuccessExample7", somePscsMarkers, listOf(OffenceSdsExclusionIndicator.NATIONAL_SECURITY, OffenceSdsExclusionIndicator.SCHEDULE_13_PART_3)),
        ),
      )
  }

  @Test
  fun `should retry and eventually get sexual or violent for multiple offence codes`() {
    mockManageOffencesClient.withStub(
      get(urlMatching("/schedule/sds-offence-details\\?offenceCodes=RetryExample1,RetryExample2,RetryExample3"))
        .inScenario("Retry Scenario")
        .whenScenarioStateIs("Started")
        .willReturn(
          aResponse()
            .withStatus(500),
        )
        .willSetStateTo("Second Attempt"),
    )

    mockManageOffencesClient.withStub(
      get(urlMatching("/schedule/sds-offence-details\\?offenceCodes=RetryExample1,RetryExample2,RetryExample3"))
        .inScenario("Retry Scenario")
        .whenScenarioStateIs("Second Attempt")
        .willReturn(
          aResponse()
            .withStatus(500),
        )
        .willSetStateTo("Third Attempt"),
    )

    mockManageOffencesClient.withStub(
      get(urlMatching("/schedule/sds-offence-details\\?offenceCodes=RetryExample1,RetryExample2,RetryExample3"))
        .inScenario("Retry Scenario")
        .whenScenarioStateIs("Third Attempt")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """[
                {
                    "offenceCode": "RetryExample1",
                    "pcscMarkers": {"inListA": false, "inListB": false, "inListC": false, "inListD": false},
                    "earlyReleaseExclusions": []
                },
                {
                    "offenceCode": "RetryExample2",
                    "pcscMarkers": {"inListA": false, "inListB": false, "inListC": false, "inListD": false},
                    "earlyReleaseExclusions": ["SEXUAL"]
                },
                {
                    "offenceCode": "RetryExample3",
                    "pcscMarkers": {"inListA": false, "inListB": false, "inListC": false, "inListD": false},
                    "earlyReleaseExclusions": ["VIOLENT"]
                }
              ]
              """.trimIndent(),
            )
            .withStatus(200),
        ),
    )

    val result = manageOffencesApiClient.getSdsOffenceDetails(listOf("RetryExample1", "RetryExample2", "RetryExample3"))

    assertThat(result).isEqualTo(
      listOf(
        SdsOffenceDetails("RetryExample1", noPscsMarkers, emptyList()),
        SdsOffenceDetails("RetryExample2", noPscsMarkers, listOf(OffenceSdsExclusionIndicator.SEXUAL)),
        SdsOffenceDetails("RetryExample3", noPscsMarkers, listOf(OffenceSdsExclusionIndicator.VIOLENT)),
      ),
    )
  }

  @Test
  fun `should throw exception when maximum retries are exceeded`() {
    mockManageOffencesClient.withStub(
      get(urlMatching("/schedule/sds-offence-details\\?offenceCodes=ErrorExample1,ErrorExample2,ErrorExample3"))
        .willReturn(
          aResponse()
            .withStatus(500),
        ),
    )

    assertThatThrownBy {
      manageOffencesApiClient.getSdsOffenceDetails(listOf("ErrorExample1", "ErrorExample2", "ErrorExample3"))
    }
      .isInstanceOf(MaxRetryAchievedException::class.java)
      .hasMessageContaining("Max retries - getSdsOffenceDetails")
  }
}
