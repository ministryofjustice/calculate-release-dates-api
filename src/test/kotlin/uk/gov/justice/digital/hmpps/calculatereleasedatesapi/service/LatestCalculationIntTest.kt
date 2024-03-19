package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateHint
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import java.time.LocalDate
import java.time.LocalDateTime

class LatestCalculationIntTest(private val mockPrisonService: MockPrisonService) : IntegrationTestBase() {

  @BeforeEach
  fun setUp() {
    mockPrisonService.withInstAgencies(
      listOf(
        Agency("ABC", "prison ABC"),
        Agency("HDC4P", "prison HDC4P"),
      ),
    )
  }

  @Test
  fun `should be able to get latest calculation when it is from NOMIS`() {
    val bookingId = 123456L
    val prisonerId = "ABC123"
    val prisonerDetails = PrisonerDetails(bookingId, prisonerId, "Joe", "Bloggs", LocalDate.of(1970, 1, 1))
    val offenderKeyDates = OffenderKeyDates("NEW", "From NOMIS", conditionalReleaseDate = LocalDate.of(2030, 1, 6), sentenceExpiryDate = LocalDate.of(2025, 2, 14))
    stubPrisoner(prisonerDetails)
    stubKeyDates(bookingId, offenderKeyDates)

    val latestCalculation = webTestClient.get()
      .uri("/calculation/$prisonerId/latest")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(LatestCalculation::class.java)
      .returnResult().responseBody!!

    assertThat(latestCalculation).isEqualTo(
      LatestCalculation(
        prisonerId,
        null,
        null,
        "NEW",
        CalculationSource.NOMIS,
        mapOf(
          ReleaseDateType.SED to DetailedDate(ReleaseDateType.SED, ReleaseDateType.SED.description, LocalDate.of(2025, 2, 14), emptyList()),
          ReleaseDateType.CRD to DetailedDate(
            ReleaseDateType.CRD,
            ReleaseDateType.CRD.description,
            LocalDate.of(2030, 1, 6),
            listOf(
              ReleaseDateHint(
                "The Discretionary Friday/Pre-Bank Holiday Release Scheme Policy applies to this release date.",
                "https://www.gov.uk/government/publications/discretionary-fridaypre-bank-holiday-release-scheme-policy-framework",
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `should be able to get latest calculation when it is from CRDS`() {
    val bookingId = 1544803905L
    val prisonerId = "default" /* stubs from JSON for sentence and offences, etc. */
    val prelim = createPreliminaryCalculation(prisonerId)
    val confirmed = createConfirmCalculationForPrisoner(prelim.calculationRequestId)

    val offenderKeyDates = OffenderKeyDates(
      "NEW",
      "From CRDS: ${confirmed.calculationReference}",
      conditionalReleaseDate = LocalDate.of(2016, 1, 6),
      topupSupervisionExpiryDate = LocalDate.of(2017, 1, 6),
      homeDetentionCurfewEligibilityDate = LocalDate.of(2015, 8, 7),
      effectiveSentenceEndDate = LocalDate.of(2016, 11, 16),
      sentenceExpiryDate = LocalDate.of(2016, 11, 6),
      licenceExpiryDate = LocalDate.of(2016, 11, 6),
    )
    stubKeyDates(bookingId, offenderKeyDates)

    val latestCalculation = webTestClient.get()
      .uri("/calculation/default/latest")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(LatestCalculation::class.java)
      .returnResult().responseBody!!

    assertThat(latestCalculation.calculatedAt).isNotNull()
    val calculatedAtOverrideForComparison = LocalDateTime.now()
    assertThat(latestCalculation.copy(calculatedAt = calculatedAtOverrideForComparison)).isEqualTo(
      LatestCalculation(
        prisonerId,
        calculatedAtOverrideForComparison,
        "",
        "Initial calculation",
        CalculationSource.CRDS,
        mapOf(
          ReleaseDateType.SLED to DetailedDate(ReleaseDateType.SLED, ReleaseDateType.SLED.description, LocalDate.of(2016, 11, 6), emptyList()),
          ReleaseDateType.SED to DetailedDate(ReleaseDateType.SED, ReleaseDateType.SED.description, LocalDate.of(2016, 11, 6), emptyList()),
          ReleaseDateType.LED to DetailedDate(ReleaseDateType.LED, ReleaseDateType.LED.description, LocalDate.of(2016, 11, 6), emptyList()),
          ReleaseDateType.HDCED to DetailedDate(ReleaseDateType.HDCED, ReleaseDateType.HDCED.description, LocalDate.of(2015, 8, 7), emptyList()),
          ReleaseDateType.CRD to DetailedDate(ReleaseDateType.CRD, ReleaseDateType.CRD.description, LocalDate.of(2016, 1, 6), emptyList()),
          ReleaseDateType.TUSED to DetailedDate(ReleaseDateType.TUSED, ReleaseDateType.TUSED.description, LocalDate.of(2017, 1, 6), emptyList()),
        ),
      ),
    )
  }

  private fun stubKeyDates(bookingId: Long, offenderKeyDates: OffenderKeyDates) {
    mockPrisonService.withStub(
      get("/api/offender-dates/$bookingId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(offenderKeyDates))
            .withStatus(200),
        ),
    )
  }

  private fun stubPrisoner(prisonerDetails: PrisonerDetails) {
    mockPrisonService.withStub(
      get("/api/offenders/${prisonerDetails.offenderNo}")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(prisonerDetails))
            .withStatus(200),
        ),
    )
  }
}
