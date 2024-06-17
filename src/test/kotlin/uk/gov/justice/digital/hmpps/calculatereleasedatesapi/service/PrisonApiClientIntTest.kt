package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.UserContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import java.time.LocalDate
import java.time.LocalDateTime

class PrisonApiClientIntTest(private val mockPrisonService: MockPrisonService) : IntegrationTestBase() {

  @Autowired
  lateinit var prisonApiClient: PrisonApiClient

  @BeforeEach
  fun setUp() {
    val headers = HttpHeaders()
    setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR"))(headers)
    UserContext.setAuthToken(headers[HttpHeaders.AUTHORIZATION]?.firstOrNull())
  }

  @Test
  fun `should get key offender dates`() {
    val bookingId = 123456L
    mockPrisonService.withStub(
      get("/api/offender-dates/$bookingId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""{ "reasonCode": "NEW", "calculatedAt": "2025-01-02T10:30:00" }""")
            .withStatus(200),
        ),
    )
    assertThat(prisonApiClient.getOffenderKeyDates(bookingId)).isEqualTo(OffenderKeyDates("NEW", LocalDateTime.of(2025, 1, 2, 10, 30, 0)).right())
  }

  @ParameterizedTest
  @CsvSource(
    "404,Booking (123456) not found or has no calculations",
    "403,User is not allowed to view the booking (123456)",
    "400,Booking (123456) could not be loaded for an unknown reason. Status 400",
    "500,Booking (123456) could not be loaded for an unknown reason. Status 500",
  )
  fun `key offender dates should throw an exception if you get a 404`(status: Int, expectedError: String) {
    val bookingId = 123456L
    mockPrisonService.withStub(
      get("/api/offender-dates/$bookingId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status),
        ),
    )
    assertThat(prisonApiClient.getOffenderKeyDates(bookingId)).isEqualTo(expectedError.left())
  }

  @ParameterizedTest
  @CsvSource(
    "400,Bad request",
    "403,User not authorised to access Tused",
    "404,No Tused could be retrieved for Nomis ID A12345AB",
    "500,Unknown: status code was 500",
  )
  fun `latest tudsed returns sensible errors`(status: Int, expectedError: String) {
    val nomisId = "A12345AB"
    mockPrisonService.withStub(
      get("/api/offender-dates/latest-tused/$nomisId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status),
        ),
    )
    assertThat(prisonApiClient.getLatestTusedDataForBotus(nomisId)).isEqualTo(expectedError.left())
  }

  @Test
  fun `can get NOMIS calc reasons`() {
    mockPrisonService.withStub(
      get("/api/reference-domains/domains/CALC_REASON/codes")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """[ { "code": "TRANSFER", "description": "Transfer Check" }, { "code": "UPDATE", "description": "Modify Sentence" } ]""",
            ),
        ),
    )
    assertThat(prisonApiClient.getNOMISCalcReasons()).isEqualTo(
      listOf(
        NomisCalculationReason("TRANSFER", "Transfer Check"),
        NomisCalculationReason("UPDATE", "Modify Sentence"),
      ),
    )
  }

  @Test
  fun `can get offender release dates by offenderSentCalcId from NOMIS`() {
    mockPrisonService.withStub(
      get("/api/offender-dates/sentence-calculation/-1")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """{
                      "dtoPostRecallReleaseDate": "2020-03-22",
                      "conditionalReleaseDate": "2019-05-24",
                      "sentenceExpiryDate": "2020-03-24",
                      "effectiveSentenceEndDate": "2025-05-05",
                      "tariffExpiredRemovalSchemeEligibilityDate": "2020-06-25",
                      "approvedParoleDate": "2018-09-27",
                      "comment": "Some Comment Text",
                      "reasonCode": "NEW",
                      "calculatedAt": "2017-08-28T00:00:00"
                  }""",
            ),
        ),
    )
    // when
    val offenderKeyDates = prisonApiClient.getNOMISOffenderKeyDates(-1)
      .getOrElse { problemMessage -> throw CrdWebException(problemMessage, HttpStatus.NOT_FOUND) }
    assertThat(offenderKeyDates.calculatedAt).isEqualTo(LocalDateTime.of(2017, 8, 28, 0, 0))
    assertThat(offenderKeyDates.reasonCode).isEqualTo("NEW")
    assertThat(offenderKeyDates.comment).isEqualTo("Some Comment Text")
    assertThat(offenderKeyDates.approvedParoleDate).isEqualTo(LocalDate.of(2018, 9, 27))
    assertThat(offenderKeyDates.tariffExpiredRemovalSchemeEligibilityDate).isEqualTo(LocalDate.of(2020, 6, 25))
    assertThat(offenderKeyDates.effectiveSentenceEndDate).isEqualTo(LocalDate.of(2025, 5, 5))
    assertThat(offenderKeyDates.sentenceExpiryDate).isEqualTo(LocalDate.of(2020, 3, 24))
    assertThat(offenderKeyDates.conditionalReleaseDate).isEqualTo(LocalDate.of(2019, 5, 24))
    assertThat(offenderKeyDates.dtoPostRecallReleaseDate).isEqualTo(LocalDate.of(2020, 3, 22))
  }

  @ParameterizedTest
  @CsvSource(
    "404,Offender Key Dates for offenderSentCalcId (123456) not found or has no calculations",
    "403,User is not allowed to view the Offender Key Dates for offenderSentCalcId (123456)",
    "400,Offender Key Dates for offenderSentCalcId (123456) could not be loaded for an unknown reason. Status 400",
    "500,Offender Key Dates for offenderSentCalcId (123456) could not be loaded for an unknown reason. Status 500",
  )
  fun `get offender release dates by offenderSentCalcId from NOMIS should throw error`(status: Int, expectedError: String) {
    val offenderSentCalcId = 123456L
    mockPrisonService.withStub(
      get("/api/offender-dates/sentence-calculation/$offenderSentCalcId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status),
        ),
    )
    assertThat(prisonApiClient.getNOMISOffenderKeyDates(offenderSentCalcId)).isEqualTo(expectedError.left())
  }
}
