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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateHint
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceDetail
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
    mockPrisonService.withNomisCalculationReasons(
      listOf(
        NomisCalculationReason("NEW", "New Sentence"),
      ),
    )
  }

  @Test
  fun `should be able to get latest calculation when it is from NOMIS`() {
    stubPrisoner(prisonerDetails)
    stubKeyDates(
      bookingId,
      OffenderKeyDates(
        "NEW",
        now,
        "From NOMIS",
        conditionalReleaseDate = LocalDate.of(2030, 1, 6),
        sentenceExpiryDate = LocalDate.of(2025, 2, 14),
      ),
    )
    stubSentenceDetails(
      bookingId,
      sentenceDetailsStub.copy(
        conditionalReleaseOverrideDate = LocalDate.of(2030, 1, 6),
      ),
    )

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
        bookingId,
        now,
        null,
        null,
        "New Sentence",
        CalculationSource.NOMIS,
        listOf(
          DetailedDate(ReleaseDateType.SED, ReleaseDateType.SED.description, LocalDate.of(2025, 2, 14), emptyList()),
          DetailedDate(
            ReleaseDateType.CRD,
            ReleaseDateType.CRD.description,
            LocalDate.of(2030, 1, 6),
            listOf(
              ReleaseDateHint("Manually overridden"),
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
    // stubs from JSON for sentence and offences, etc.
    val prisonerId = "default"
    val prelim = createPreliminaryCalculation(prisonerId)
    val confirmed = createConfirmCalculationForPrisoner(prelim.calculationRequestId)

    val offenderKeyDates = OffenderKeyDates(
      "NEW",
      now,
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
        bookingId,
        calculatedAtOverrideForComparison,
        confirmed.calculationRequestId,
        "",
        "Initial calculation",
        CalculationSource.CRDS,
        listOf(
          DetailedDate(
            ReleaseDateType.SLED,
            ReleaseDateType.SLED.description,
            LocalDate.of(2016, 11, 6),
            emptyList(),
          ),
          DetailedDate(ReleaseDateType.CRD, ReleaseDateType.CRD.description, LocalDate.of(2016, 1, 6), emptyList()),
          DetailedDate(
            ReleaseDateType.HDCED,
            ReleaseDateType.HDCED.description,
            LocalDate.of(2015, 8, 7),
            emptyList(),
          ),
          DetailedDate(ReleaseDateType.TUSED, ReleaseDateType.TUSED.description, LocalDate.of(2017, 1, 6), emptyList()),
        ),
      ),
    )
  }

  private fun stubSentenceDetails(bookingId: Long, sentenceDetail: SentenceDetail) {
    mockPrisonService.withStub(
      get("/api/bookings/$bookingId/sentenceDetail")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(sentenceDetail))
            .withStatus(200),
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

  companion object {
    private val now = LocalDateTime.now()
    val bookingId = 123456L
    val prisonerId = "ABC123"
    val prisonerDetails = PrisonerDetails(bookingId, prisonerId, "Joe", "Bloggs", LocalDate.of(1970, 1, 1))
    private val sentenceDetailsStub = SentenceDetail(
      sentenceExpiryDate = null,
      automaticReleaseDate = null,
      conditionalReleaseDate = null,
      nonParoleDate = null,
      postRecallReleaseDate = null,
      licenceExpiryDate = LocalDate.of(2016, 11, 6),
      homeDetentionCurfewEligibilityDate = null,
      paroleEligibilityDate = null,
      homeDetentionCurfewActualDate = null,
      actualParoleDate = null,
      releaseOnTemporaryLicenceDate = null,
      earlyRemovalSchemeEligibilityDate = null,
      earlyTermDate = null,
      midTermDate = null,
      lateTermDate = null,
      topupSupervisionExpiryDate = LocalDate.of(2017, 1, 6),
      tariffDate = null,
      dtoPostRecallReleaseDate = null,
      tariffEarlyRemovalSchemeEligibilityDate = null,
      effectiveSentenceEndDate = LocalDate.of(2016, 11, 16),
      bookingId = 123,
      sentenceStartDate = LocalDate.of(2016, 11, 6),
      additionalDaysAwarded = 0,
      automaticReleaseOverrideDate = null,
      conditionalReleaseOverrideDate = null,
      nonParoleOverrideDate = null,
      postRecallReleaseOverrideDate = null,
      dtoPostRecallReleaseDateOverride = null,
      nonDtoReleaseDate = null,
      sentenceExpiryCalculatedDate = null,
      sentenceExpiryOverrideDate = null,
      licenceExpiryCalculatedDate = null,
      licenceExpiryOverrideDate = null,
      paroleEligibilityCalculatedDate = null,
      paroleEligibilityOverrideDate = null,
      topupSupervisionExpiryCalculatedDate = null,
      topupSupervisionExpiryOverrideDate = null,
      homeDetentionCurfewEligibilityCalculatedDate = null,
      homeDetentionCurfewEligibilityOverrideDate = null,
      nonDtoReleaseDateType = "CRD",
      confirmedReleaseDate = null,
      releaseDate = null,
      etdOverrideDate = null,
      etdCalculatedDate = null,
      mtdOverrideDate = null,
      mtdCalculatedDate = null,
      ltdOverrideDate = null,
      ltdCalculatedDate = null,
      topupSupervisionStartDate = null,
      homeDetentionCurfewEndDate = null,
    )
  }
}
