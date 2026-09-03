package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.github.tomakehurst.wiremock.client.WireMock.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageUsersClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageusersapi.model.PrisonUserBasicDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerCalculationOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateHint
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceDetail
import java.time.LocalDate
import java.time.LocalDateTime

class PrisonerCalculationOverviewIntTest(private val mockPrisonService: MockPrisonService, private val mockManageUsersClient: MockManageUsersClient) : IntegrationTestBase() {

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
    mockManageUsersClient.withUsers(
      listOf(
        PrisonUserBasicDetails(
          "TEST-CLIENT", "Test", lastName = "Client",
          staffId = 123, enabled = true, userId = 1, name = "Test Client",
          authSource = PrisonUserBasicDetails.AuthSource.auth,
          activeCaseloadId = null, accountStatus = null, primaryEmail = null,
        ),
        PrisonUserBasicDetails(
          "USER1", "User", lastName = "Name",
          staffId = 321, enabled = true, userId = 2, name = "User Name",
          authSource = PrisonUserBasicDetails.AuthSource.auth,
          activeCaseloadId = null, accountStatus = null, primaryEmail = null,
        ),
        PrisonUserBasicDetails(
          "USER2", "User", lastName = "Two",
          staffId = 456, enabled = true, userId = 3, name = "User Two",
          authSource = PrisonUserBasicDetails.AuthSource.auth,
          activeCaseloadId = null, accountStatus = null, primaryEmail = null,
        ),
      ),
    )
  }

  @Test
  fun `should be able to get overview when latest calculation is from NOMIS`() {
    val (bookingId, prisonerId, confirmed) = createCrdsCalculation()

    mockPrisonService.stubPrisoner(prisonerDetails.copy(bookingId = bookingId, offenderNo = prisonerId))
    mockPrisonService.stubKeyDates(
      bookingId,
      OffenderKeyDates(
        "NEW",
        now.plusDays(1), // make latest
        "From NOMIS",
        conditionalReleaseDate = LocalDate.of(2030, 1, 6),
        sentenceExpiryDate = LocalDate.of(2025, 2, 14),
        conditionalReleaseDateOverridden = true,
        calculatedByUserId = "user1",
        calculatedByFirstName = "User",
        calculatedByLastName = "One",
      ),
    )
    mockPrisonService.stubSentenceDetails(
      bookingId,
      sentenceDetailsStub.copy(
        conditionalReleaseOverrideDate = LocalDate.of(2030, 1, 6),
      ),
    )

    mockPrisonService.stubHistoricCalculations(
      prisonerId,
      listOf(
        SentenceCalculationSummary(
          bookingId = bookingId,
          offenderNo = prisonerId,
          firstName = prisonerDetails.firstName,
          lastName = prisonerDetails.lastName,
          agencyLocationId = "ABC",
          agencyDescription = "Some agency description from NOMIS",
          offenderSentCalculationId = 123456789,
          calculationDate = now.plusDays(1), // same as key dates
          staffId = 321,
          calculationReason = "NEW",
          calculatedByUserId = "user1",
          calculatedByFirstName = "User",
          calculatedByLastName = "Name",
          commentText = "NOMIS calc",
        ),
        SentenceCalculationSummary(
          bookingId = bookingId,
          offenderNo = prisonerId,
          firstName = prisonerDetails.firstName,
          lastName = prisonerDetails.lastName,
          agencyLocationId = "ABC",
          agencyDescription = "Some agency description from NOMIS",
          offenderSentCalculationId = 987654321,
          calculationDate = now,
          staffId = 321,
          calculationReason = "NEW",
          calculatedByUserId = "user1",
          calculatedByFirstName = "User",
          calculatedByLastName = "Name",
          commentText = "CRDS calc ${confirmed.calculationReference}",
        ),
      ),
    )

    val latestCalculation = webTestClient.get()
      .uri("/calculation/$prisonerId/overview")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("CALCULATE_RELEASE_DATES__CALCULATE__RO")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(PrisonerCalculationOverview::class.java)
      .returnResult().responseBody!!

    assertThat(latestCalculation).usingRecursiveComparison().ignoringFieldsMatchingRegexes(".*calculatedAt.*", ".*calculationDate.*").isEqualTo(
      PrisonerCalculationOverview(
        latestCalculation = LatestCalculation(
          prisonerId,
          bookingId,
          now,
          null,
          null,
          null,
          "New Sentence",
          null,
          null,
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
          "user1",
          null,
          "User Name",
          null,
          calculationType = "Unknown",
        ),
        recentCalculations = listOf(
          HistoricCalculationSummary(
            calculationDate = now.plusDays(1),
            calculationSource = CalculationSource.NOMIS,
            calculationType = null,
            crdsCalculationId = null,
            nomisCalculationId = 123456789,
            reasonDescription = "New Sentence",
            reasonFurtherDetail = null,
            genuineOverrideReasonDescription = null,
            calculatedByDisplayName = "User Name",
            establishmentCalculatedAtDescription = "Some agency description from NOMIS",
          ),
          HistoricCalculationSummary(
            calculationDate = now,
            calculationSource = CalculationSource.CRDS,
            calculationType = CalculationType.CALCULATED,
            crdsCalculationId = confirmed.calculationRequestId,
            nomisCalculationId = 987654321,
            reasonDescription = "Initial calculation",
            reasonFurtherDetail = null,
            genuineOverrideReasonDescription = null,
            calculatedByDisplayName = "Test Client",
            establishmentCalculatedAtDescription = "",
          ),
        ),
        totalCalculationCount = 2,
        numberOfSentences = 1,
        hasIndeterminateSentences = false,
      ),
    )
  }

  @Test
  fun `should be able to get overview when the latest calculation is from CRDS`() {
    val (bookingId, prisonerId, confirmed) = createCrdsCalculation()

    mockPrisonService.stubHistoricCalculations(
      prisonerId,
      listOf(
        SentenceCalculationSummary(
          bookingId = bookingId,
          offenderNo = prisonerId,
          firstName = prisonerDetails.firstName,
          lastName = prisonerDetails.lastName,
          agencyLocationId = "ABC",
          agencyDescription = "Some agency description from NOMIS",
          offenderSentCalculationId = 987654321,
          calculationDate = now,
          staffId = 321,
          calculationReason = "NEW",
          calculatedByUserId = "user1",
          calculatedByFirstName = "User",
          calculatedByLastName = "Name",
          commentText = "CRDS calc ${confirmed.calculationReference}",
        ),
        SentenceCalculationSummary(
          bookingId = bookingId,
          offenderNo = prisonerId,
          firstName = prisonerDetails.firstName,
          lastName = prisonerDetails.lastName,
          agencyLocationId = "ABC",
          agencyDescription = "Some agency description from NOMIS",
          offenderSentCalculationId = 123456789,
          calculationDate = now.minusDays(1),
          staffId = 321,
          calculationReason = "NEW",
          calculatedByUserId = "user1",
          calculatedByFirstName = "User",
          calculatedByLastName = "Name",
          commentText = "NOMIS calc",
        ),
      ),
    )

    val latestCalculation = webTestClient.get()
      .uri("/calculation/$prisonerId/overview")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("CALCULATE_RELEASE_DATES__CALCULATE__RO")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(PrisonerCalculationOverview::class.java)
      .returnResult().responseBody!!

    assertThat(latestCalculation).usingRecursiveComparison().ignoringFieldsMatchingRegexes(".*calculatedAt.*", ".*calculationDate.*").isEqualTo(
      PrisonerCalculationOverview(
        latestCalculation = LatestCalculation(
          prisonerId,
          bookingId,
          now,
          null,
          confirmed.calculationRequestId,
          "",
          "Initial calculation",
          null,
          null,
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
          "test-client",
          null,
          "Test Client",
          null,
          calculationType = CalculationType.CALCULATED.name,
        ),
        recentCalculations = listOf(
          HistoricCalculationSummary(
            calculationDate = now,
            calculationSource = CalculationSource.CRDS,
            calculationType = CalculationType.CALCULATED,
            crdsCalculationId = confirmed.calculationRequestId,
            nomisCalculationId = 987654321,
            reasonDescription = "Initial calculation",
            reasonFurtherDetail = null,
            genuineOverrideReasonDescription = null,
            calculatedByDisplayName = "Test Client",
            establishmentCalculatedAtDescription = "",
          ),
          HistoricCalculationSummary(
            calculationDate = now.minusDays(1),
            calculationSource = CalculationSource.NOMIS,
            calculationType = null,
            crdsCalculationId = null,
            nomisCalculationId = 123456789,
            reasonDescription = "New Sentence",
            reasonFurtherDetail = null,
            genuineOverrideReasonDescription = null,
            calculatedByDisplayName = "User Name",
            establishmentCalculatedAtDescription = "Some agency description from NOMIS",
          ),
        ),
        totalCalculationCount = 2,
        numberOfSentences = 1,
        hasIndeterminateSentences = false,
      ),
    )
  }

  private fun createCrdsCalculation(): Triple<Long, String, CalculatedReleaseDates> {
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
      calculatedByUserId = "test-client",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
    )
    mockPrisonService.stubKeyDates(bookingId, offenderKeyDates)
    return Triple(bookingId, prisonerId, confirmed)
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
