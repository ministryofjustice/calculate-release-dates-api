package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.UserContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
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
}
