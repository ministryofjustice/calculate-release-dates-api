package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideCreatedResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.CalculationIntTest.Companion.PRISONER_ID
import java.time.LocalDate

@Sql(scripts = ["classpath:/test_data/reset-base-data.sql", "classpath:/test_data/load-base-data.sql"])
class GenuineOverrideIntTest(private val mockPrisonService: MockPrisonService) : IntegrationTestBase() {

  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Test
  fun `should create a new calculation using the overridden dates and update the original calculation to say it was overridden`() {
    val preliminaryCalculation = createPreliminaryCalculation(PRISONER_ID)
    val request = GenuineOverrideRequest(
      dates = listOf(
        GenuineOverrideDate(ReleaseDateType.SED, LocalDate.of(2025, 1, 2)),
        GenuineOverrideDate(ReleaseDateType.LED, LocalDate.of(2029, 12, 13)),
        GenuineOverrideDate(ReleaseDateType.HDCED, LocalDate.of(2021, 6, 7)),
      ),
      reason = GenuineOverrideReason.TERRORISM,
      reasonFurtherDetail = null,
    )
    val response = webTestClient.post()
      .uri("/genuine-override/calculation/${preliminaryCalculation.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(GenuineOverrideCreatedResponse::class.java)
      .returnResult().responseBody!!

    assertThat(response.originalCalculationRequestId).isEqualTo(preliminaryCalculation.calculationRequestId)
    val originalRequest = calculationRequestRepository.findById(response.originalCalculationRequestId).orElseThrow { fail("Couldn't load original request") }
    val newRequest = calculationRequestRepository.findById(response.newCalculationRequestId).orElseThrow { fail("Couldn't load new request") }

    assertThat(originalRequest.overridesCalculationRequestId).isNull()
    assertThat(originalRequest.overriddenByCalculationRequestId).isEqualTo(newRequest.id)
    assertThat(originalRequest.genuineOverrideReason).isEqualTo(GenuineOverrideReason.TERRORISM)
    assertThat(originalRequest.genuineOverrideReasonFurtherDetail).isNull()
    assertThat(originalRequest.calculationStatus).isEqualTo(CalculationStatus.OVERRIDDEN.name)

    assertThat(newRequest.overridesCalculationRequestId).isEqualTo(originalRequest.id)
    assertThat(newRequest.overriddenByCalculationRequestId).isNull()
    assertThat(newRequest.genuineOverrideReason).isEqualTo(GenuineOverrideReason.TERRORISM)
    assertThat(newRequest.genuineOverrideReasonFurtherDetail).isNull()
    assertThat(newRequest.calculationStatus).isEqualTo(CalculationStatus.CONFIRMED.name)

    // Check the latest results are from the override
    val result = webTestClient.get()
      .uri("/calculation/results/${preliminaryCalculation.prisonerId}/${preliminaryCalculation.bookingId}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody

    assertThat(result).isNotNull
    assertThat(result!!.calculationRequestId).isEqualTo(newRequest.id)
    assertThat(result.dates).containsOnlyKeys(ReleaseDateType.SED, ReleaseDateType.LED, ReleaseDateType.HDCED)
    assertThat(result.dates[ReleaseDateType.SED]!!).isEqualTo(LocalDate.of(2025, 1, 2))
    assertThat(result.dates[ReleaseDateType.LED]!!).isEqualTo(LocalDate.of(2029, 12, 13))
    assertThat(result.dates[ReleaseDateType.HDCED]!!).isEqualTo(LocalDate.of(2021, 6, 7))

    mockPrisonService.verify(
      postRequestedFor(urlPathEqualTo("/api/offender-dates/${preliminaryCalculation.bookingId}"))
        .withRequestBody(matchingJsonPath("$.keyDates[?(@.licenceExpiryDate == '2029-12-13')]"))
        .withRequestBody(matchingJsonPath("$.keyDates[?(@.sentenceExpiryDate == '2025-01-02')]"))
        .withRequestBody(matchingJsonPath("$.keyDates[?(@.homeDetentionCurfewEligibilityDate == '2021-06-07')]"))
        .withRequestBody(matchingJsonPath("$.comment", containing("{Initial calculation} using the Calculate Release Dates service via override. The calculation ID is: ${newRequest.calculationReference}"))),
    )
  }

  @Test
  fun `should create a genuine override with reason other and further detail`() {
    val preliminaryCalculation = createPreliminaryCalculation(PRISONER_ID)
    val request = GenuineOverrideRequest(
      dates = listOf(
        GenuineOverrideDate(ReleaseDateType.SED, LocalDate.of(2025, 1, 2)),
        GenuineOverrideDate(ReleaseDateType.LED, LocalDate.of(2029, 12, 13)),
        GenuineOverrideDate(ReleaseDateType.HDCED, LocalDate.of(2021, 6, 7)),
      ),
      reason = GenuineOverrideReason.OTHER,
      reasonFurtherDetail = "A test reason",
    )
    val response = webTestClient.post()
      .uri("/genuine-override/calculation/${preliminaryCalculation.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(GenuineOverrideCreatedResponse::class.java)
      .returnResult().responseBody!!

    assertThat(response.originalCalculationRequestId).isEqualTo(preliminaryCalculation.calculationRequestId)
    val originalRequest = calculationRequestRepository.findById(response.originalCalculationRequestId).orElseThrow { fail("Couldn't load original request") }
    val newRequest = calculationRequestRepository.findById(response.newCalculationRequestId).orElseThrow { fail("Couldn't load new request") }

    assertThat(originalRequest.overridesCalculationRequestId).isNull()
    assertThat(originalRequest.overriddenByCalculationRequestId).isEqualTo(newRequest.id)
    assertThat(originalRequest.genuineOverrideReason).isEqualTo(GenuineOverrideReason.OTHER)
    assertThat(originalRequest.genuineOverrideReasonFurtherDetail).isEqualTo("A test reason")
    assertThat(originalRequest.calculationStatus).isEqualTo(CalculationStatus.OVERRIDDEN.name)

    assertThat(newRequest.overridesCalculationRequestId).isEqualTo(originalRequest.id)
    assertThat(newRequest.overriddenByCalculationRequestId).isNull()
    assertThat(newRequest.genuineOverrideReason).isEqualTo(GenuineOverrideReason.OTHER)
    assertThat(newRequest.genuineOverrideReasonFurtherDetail).isEqualTo("A test reason")
    assertThat(newRequest.calculationStatus).isEqualTo(CalculationStatus.CONFIRMED.name)
  }

  @Test
  fun `should return a 404 if the original calculation cannot be found`() {
    val request = GenuineOverrideRequest(
      dates = listOf(
        GenuineOverrideDate(ReleaseDateType.SED, LocalDate.of(2025, 1, 2)),
      ),
      reason = GenuineOverrideReason.OTHER,
      reasonFurtherDetail = "A test reason",
    )
    webTestClient.post()
      .uri("/genuine-override/calculation/-1000")
      .accept(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isNotFound
  }
}
