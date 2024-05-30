package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.UserContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ManageOffencesApiClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.OffenceSDSReleaseArrangementLookupService
import java.time.LocalDate

class OffenceSDSReleaseArrangementLookupServiceIntTest(private val mockManageOffencesClient: MockManageOffencesClient) : IntegrationTestBase() {

  @Autowired
  lateinit var offenceSDSReleaseArrangementLookupService: OffenceSDSReleaseArrangementLookupService

  @BeforeEach
  fun setUp() {
    val headers = HttpHeaders()
    setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR"))(headers)
    UserContext.setAuthToken(headers[HttpHeaders.AUTHORIZATION]?.firstOrNull())
  }

  @Test
  fun `Test Call to MO Service Matching SEC_250 Offence marked as SDS+`() {
    // S250 over 7 years and sentenced after PCSC date
    val inputOffenceList = listOf(
      NormalisedSentenceAndOffence(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.SEC250.toString(),
        "TEST",
        LocalDate.of(2022, 8, 29),
        listOf(SentenceTerms(8, 4, 1, 1)),
        OffenderOffence(
          1,
          LocalDate.of(2022, 1, 1),
          null,
          "COML025A",
          "TEST OFFENSE",
        ),
        null,
        null,
        null,
      ),
    )

    UserContext.setAuthToken("123456")
    val markedUp = offenceSDSReleaseArrangementLookupService.populateReleaseArrangements(inputOffenceList)
    assertTrue(markedUp[0].isSDSPlus)
  }

  @Test
  fun `Test exception is thrown on 500 MO response`() {
    mockManageOffencesClient.withStub(
      get(urlMatching("/schedule/sexual-or-violent\\?offenceCodes=500Response"))
        .willReturn(
          aResponse()
            .withStatus(500),
        ),
    )

    val exception = assertThrows<Exception> {
      val inputOffenceList = listOf(
        NormalisedSentenceAndOffence(
          1,
          1,
          1,
          1,
          null,
          "TEST",
          "TEST",
          SentenceCalculationType.SEC250.toString(),
          "TEST",
          LocalDate.of(2022, 8, 29),
          listOf(SentenceTerms(8, 4, 1, 1)),
          OffenderOffence(
            1,
            LocalDate.of(2022, 1, 1),
            null,
            "500Response",
            "TEST OFFENSE",
          ),
          null,
          null,
          null,
        ),
      )

      UserContext.setAuthToken("123456")
      offenceSDSReleaseArrangementLookupService.populateReleaseArrangements(inputOffenceList)
    }

    assertTrue(exception is ManageOffencesApiClient.MaxRetryAchievedException)
  }

  @Test
  fun `Test Call to MO Service Matching SEC_250 multiple offences marked as SDS+ `() {
    // S250 over 7 years and sentenced after PCSC date
    val inputOffenceList = listOf(
      NormalisedSentenceAndOffence(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.SEC250.toString(),
        "TEST",
        LocalDate.of(2022, 8, 29),
        listOf(SentenceTerms(8, 4, 1, 1)),
        OffenderOffence(
          1,
          LocalDate.of(2022, 1, 1),
          null,
          "COML025A",
          "TEST OFFENSE",
        ),
        null,
        null,
        null,
      ),
      NormalisedSentenceAndOffence(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.SEC250.toString(),
        "TEST",
        LocalDate.of(2022, 8, 29),
        listOf(SentenceTerms(8, 4, 1, 1)),
        OffenderOffence(
          1,
          LocalDate.of(2022, 1, 1),
          null,
          "COML022",
          "TEST OFFENSE",
        ),
        null,
        null,
        null,
      ),
    )

    UserContext.setAuthToken("123456")
    val markedUp = offenceSDSReleaseArrangementLookupService.populateReleaseArrangements(inputOffenceList)
    assertTrue(markedUp[0].isSDSPlus)
  }
}
