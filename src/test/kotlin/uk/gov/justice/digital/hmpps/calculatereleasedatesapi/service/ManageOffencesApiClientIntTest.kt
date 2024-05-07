package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.UserContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SDSEarlyReleaseExclusionForOffenceCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SDSEarlyReleaseExclusionSchedulePart

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
      get(urlMatching("/schedule/sexual-or-violent\\?offenceCodes=([A-Za-z0-9,]+)"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """[
                {
                    "offenceCode": "N01",
                    "schedulePart": "NONE"
                },
                {
                    "offenceCode": "S01",
                    "schedulePart": "SEXUAL"
                },
                {
                    "offenceCode": "V01",
                    "schedulePart": "VIOLENT"
                }
            ]
              """.trimIndent(),
            )
            .withStatus(200)
            .withTransformers("response-template"),
        ),
    )
    assertThat(manageOffencesApiClient.getSexualOrViolentForOffenceCodes(listOf("S01", "V01", "N01")))
      .isEqualTo(
        listOf(
          SDSEarlyReleaseExclusionForOffenceCode("N01", SDSEarlyReleaseExclusionSchedulePart.NONE),
          SDSEarlyReleaseExclusionForOffenceCode("S01", SDSEarlyReleaseExclusionSchedulePart.SEXUAL),
          SDSEarlyReleaseExclusionForOffenceCode("V01", SDSEarlyReleaseExclusionSchedulePart.VIOLENT),
        ),
      )
  }
}
