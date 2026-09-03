package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client.ManageUsersApiClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequestSecondCheck
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageusersapi.model.PrisonUserBasicDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BreakdownMissingReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerCalculationOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeHistoricOverrideRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID
import java.util.stream.Stream
import kotlin.math.min

class LatestCalculationServiceTest {

  private val prisonService: PrisonService = mock()
  private val calculationRequestRepository: CalculationRequestRepository = mock()
  private val calculationResultEnrichmentService: CalculationResultEnrichmentService = mock()
  private val calculationBreakdownService: CalculationBreakdownService = mock()
  private val historicOverrideRepository = mock<CalculationOutcomeHistoricOverrideRepository>()
  private val sourceDataMapper: SourceDataMapper = mock()
  private val offenderKeyDatesService: OffenderKeyDatesService = mock()
  private val manageUsersApiClient: ManageUsersApiClient = mock()
  private val service = LatestCalculationService(
    prisonService,
    offenderKeyDatesService,
    calculationRequestRepository,
    calculationResultEnrichmentService,
    calculationBreakdownService,
    historicOverrideRepository,
    sourceDataMapper,
    manageUsersApiClient,
  )
  private val objectMapper = TestUtil.objectMapper()
  private val prisonerId = "ABC123"
  private val bookingId = 123456L
  private val prisonerDetails = PrisonerDetails(
    bookingId,
    prisonerId,
    "John",
    "Smith",
    LocalDate.of(1970, 1, 1),
  )
  private val now = LocalDateTime.now()
  val reference: UUID = UUID.randomUUID()
  private val usersDetails = mapOf(
    "USERNAME" to PrisonUserBasicDetails(
      username = "CRD_TEST_USER",
      firstName = "User",
      lastName = "Name",
      authSource = PrisonUserBasicDetails.AuthSource.nomis,
      enabled = true,
      staffId = 12345,
      userId = 67890,
      name = "Crd Test User",
    ),
    "USERNAME1" to PrisonUserBasicDetails(
      username = "CRD_TEST_USER",
      firstName = "User",
      lastName = "Name 1",
      authSource = PrisonUserBasicDetails.AuthSource.nomis,
      enabled = true,
      staffId = 12345,
      userId = 67890,
      name = "Crd Test User",
    ),
  )

  @Test
  fun `should return a problem if could not load prisoner details for latest calculation`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenThrow(WebClientResponseException(404, "Not found", null, null, null))

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo("Prisoner ($prisonerId) could not be found".left())
  }

  @Test
  fun `should return a problem if could not load prisoner details for prisoner overview`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenThrow(WebClientResponseException(404, "Not found", null, null, null))

    assertThat(service.latestCalculationOverviewForPrisoner(prisonerId, 7)).isEqualTo("Prisoner ($prisonerId) could not be found".left())
  }

  @Test
  fun `should throw other unhandled exceptions loading prisoner details for latest calculation`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenThrow(WebClientResponseException(500, "Boom", null, null, null))

    assertThrows<WebClientResponseException>("Boom") {
      service.latestCalculationForPrisoner(prisonerId)
    }
  }

  @Test
  fun `should throw other unhandled exceptions loading prisoner details for prisoner overview`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenThrow(WebClientResponseException(500, "Boom", null, null, null))

    assertThrows<WebClientResponseException>("Boom") {
      service.latestCalculationOverviewForPrisoner(prisonerId, 5)
    }
  }

  @Test
  fun `should return a problem if could not load key dates from prison API for latest calculation`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    val expectedError = "Bang!"
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(expectedError.left())

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(expectedError.left())
  }

  @Test
  fun `should return a successful overview if there are no key dates for a prisoner`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn("Bang!".left())

    assertThat(service.latestCalculationOverviewForPrisoner(prisonerId, 5)).isEqualTo(
      PrisonerCalculationOverview(null, emptyList(), 0, 0, false).right(),
    )
  }

  @Test
  fun `should throw other unhandled exceptions loading prisoner key dates for latest calculation`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenThrow(WebClientResponseException(500, "Boom", null, null, null))

    assertThrows<WebClientResponseException>("Boom") {
      service.latestCalculationForPrisoner(prisonerId)
    }
  }

  @ParameterizedTest
  @MethodSource("latestCalculationGenerators")
  fun `if there are no CRDS calcs then return as NOMIS`(name: String, getLatestCalculation: (prisonerId: String, latestCalculationService: LatestCalculationService) -> Either<String, LatestCalculation>) {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getNOMISCalcReasons()).thenReturn(listOf(NomisCalculationReason("NEW", "New Sentence")))
    val offenderKeyDates = OffenderKeyDates(reasonCode = "NEW", calculatedAt = now, calculatedByUserId = "username", calculatedByFirstName = "User", calculatedByLastName = "One")
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.empty())
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(emptyList())
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))

    assertThat(getLatestCalculation(prisonerId, service)).describedAs(name).isEqualTo(
      LatestCalculation(
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
        emptyList(),
        "username",
        null,
        "User Name",
        null,
        calculationType = "Unknown",
      ).right(),
    )

    verify(manageUsersApiClient).getUsersByUsernames(setOf("USERNAME"))
  }

  @ParameterizedTest
  @MethodSource("latestCalculationGenerators")
  fun `if there is a username from NOMIS then just return the username and display name from manage users`(
    name: String,
    getLatestCalculation: (prisonerId: String, latestCalculationService: LatestCalculationService) -> Either<String, LatestCalculation>,
  ) {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getNOMISCalcReasons()).thenReturn(listOf(NomisCalculationReason("NEW", "New Sentence")))
    val offenderKeyDates = OffenderKeyDates(reasonCode = "NEW", calculatedAt = now, calculatedByUserId = "username", calculatedByFirstName = "User", calculatedByLastName = "Overridden By Manage Users Name")
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.empty())
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(emptyList())
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))

    assertThat(getLatestCalculation(prisonerId, service)).describedAs(name).isEqualTo(
      LatestCalculation(
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
        emptyList(),
        "username",
        null,
        "User Name",
        null,
        calculationType = "Unknown",
      ).right(),
    )

    verify(manageUsersApiClient).getUsersByUsernames(setOf("USERNAME"))
  }

  @ParameterizedTest
  @MethodSource("latestCalculationGenerators")
  fun `Should use the NOMIS calculation if the comment doesn't contain the CRDS calc reference`(
    name: String,
    getLatestCalculation: (prisonerId: String, latestCalculationService: LatestCalculationService) -> Either<String, LatestCalculation>,
  ) {
    val offenderKeyDates = OffenderKeyDates(reasonCode = "NEW", calculatedAt = now, comment = "Not this one", calculatedByUserId = "username", calculatedByFirstName = "User", calculatedByLastName = "One")
    val calculationRequest = CalculationRequest(calculationReference = UUID.randomUUID())

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getNOMISCalcReasons()).thenReturn(listOf(NomisCalculationReason("NEW", "New Sentence")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.of(calculationRequest))
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(listOf(calculationRequest))
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))

    assertThat(getLatestCalculation(prisonerId, service)).describedAs(name).isEqualTo(
      LatestCalculation(
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
        emptyList(),
        "username",
        null,
        "User Name",
        null,
        "Unknown",
      ).right(),
    )
  }

  @ParameterizedTest
  @MethodSource("latestCalculationGenerators")
  fun `Should use the NOMIS calculation if the comment is null`(name: String, getLatestCalculation: (prisonerId: String, latestCalculationService: LatestCalculationService) -> Either<String, LatestCalculation>) {
    val calculationRequest = CalculationRequest(calculationReference = UUID.randomUUID())
    val offenderKeyDates = OffenderKeyDates(reasonCode = "NEW", calculatedAt = now, calculatedByUserId = "username", calculatedByFirstName = "User", calculatedByLastName = "One")
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getNOMISCalcReasons()).thenReturn(listOf(NomisCalculationReason("NEW", "New Sentence")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.of(calculationRequest))
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(listOf(calculationRequest))
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))

    assertThat(getLatestCalculation(prisonerId, service)).describedAs(name).isEqualTo(
      LatestCalculation(
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
        emptyList(),
        "username",
        null,
        "User Name",
        null,
        "Unknown",
      ).right(),
    )
  }

  @ParameterizedTest
  @MethodSource("latestCalculationGenerators")
  fun `Should use the NOMIS reason code for reason if we can't find the looked up code`(name: String, getLatestCalculation: (prisonerId: String, latestCalculationService: LatestCalculationService) -> Either<String, LatestCalculation>) {
    val offenderKeyDates = OffenderKeyDates(reasonCode = "FOO", calculatedAt = now, calculatedByUserId = "username", calculatedByFirstName = "User", calculatedByLastName = "One")
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.empty())
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(emptyList())
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))

    assertThat(getLatestCalculation(prisonerId, service)).describedAs(name).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        now,
        null,
        null,
        null,
        "FOO",
        null,
        null,
        CalculationSource.NOMIS,
        emptyList(),
        "username",
        null,
        "User Name",
        null,
        "Unknown",
      ).right(),
    )
  }

  @ParameterizedTest
  @MethodSource("latestCalculationGenerators")
  fun `Should map CRDS additional fields into the results if the CRDS calc ref appears in the comment`(
    name: String,
    getLatestCalculation: (prisonerId: String, latestCalculationService: LatestCalculationService) -> Either<String, LatestCalculation>,
  ) {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()
    val offenderKeyDates = OffenderKeyDates(
      sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      licenceExpiryDate = LocalDate.of(2025, 1, 2),
      conditionalReleaseDate = LocalDate.of(2025, 1, 7),
      reasonCode = "NEW",
      calculatedAt = calculatedAt,
      comment = "Some stuff and then the ref: $calculationReference",
      calculatedByUserId = "user1",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
    )
    val secondCheck = secondCheckRecord()

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)

    val calculationRequest = CalculationRequest(
      id = 654321,
      calculationReference = calculationReference,
      calculatedAt = calculatedAt,
      reasonForCalculation = CalculationReason(0, false, false, "Some reason", false, null, null, null, false, false, false, null, isSecondCheck = false),
      calculatedByUsername = "username",
      secondChecks = mutableListOf(secondCheck),
      genuineOverrideReason = GenuineOverrideReason.POWER_TO_DETAIN,
    )
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.of(calculationRequest))

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED),
      ReleaseDate(LocalDate.of(2025, 1, 2), ReleaseDateType.LED),
      ReleaseDate(LocalDate.of(2025, 1, 7), ReleaseDateType.CRD),
    )
    whenever(offenderKeyDatesService.releaseDates(offenderKeyDates)).thenReturn(dates)
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null, null, null, null)).thenReturn(detailedDates.associateBy { it.type })
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(listOf(calculationRequest))
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))

    assertThat(getLatestCalculation(prisonerId, service)).describedAs(name).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        calculatedAt,
        secondCheck.checkedAt,
        654321,
        null,
        "Some reason",
        null,
        "An application for a power to detain has been approved",
        CalculationSource.CRDS,
        detailedDates,
        "username",
        secondCheck.checkedByUsername,
        "User Name",
        "User Name",
        calculationType = CalculationType.CALCULATED.name,
      ).right(),
    )
  }

  @ParameterizedTest
  @MethodSource("latestCalculationGenerators")
  fun `Should map and return username for second check display name if it is not found in manage users api`(
    name: String,
    getLatestCalculation: (prisonerId: String, latestCalculationService: LatestCalculationService) -> Either<String, LatestCalculation>,
  ) {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()
    val offenderKeyDates = OffenderKeyDates(
      sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      licenceExpiryDate = LocalDate.of(2025, 1, 2),
      conditionalReleaseDate = LocalDate.of(2025, 1, 7),
      reasonCode = "NEW",
      calculatedAt = calculatedAt,
      comment = "Some stuff and then the ref: $calculationReference",
      calculatedByUserId = "user1",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
    )
    val secondCheck = secondCheckRecordWithUnknownUsername()

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)

    val calculationRequest = CalculationRequest(
      id = 654321,
      calculationReference = calculationReference,
      calculatedAt = calculatedAt,
      reasonForCalculation = CalculationReason(0, false, false, "Some reason", false, null, null, null, false, false, false, null, isSecondCheck = false),
      calculatedByUsername = "username",
      secondChecks = mutableListOf(secondCheck),
    )
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.of(calculationRequest))

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED),
      ReleaseDate(LocalDate.of(2025, 1, 2), ReleaseDateType.LED),
      ReleaseDate(LocalDate.of(2025, 1, 7), ReleaseDateType.CRD),
    )
    whenever(offenderKeyDatesService.releaseDates(offenderKeyDates)).thenReturn(dates)
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null, null, null, null)).thenReturn(detailedDates.associateBy { it.type })
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(listOf(calculationRequest))
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))

    assertThat(getLatestCalculation(prisonerId, service)).describedAs(name).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        calculatedAt,
        secondCheck.checkedAt,
        654321,
        null,
        "Some reason",
        null,
        null,
        CalculationSource.CRDS,
        detailedDates,
        "username",
        secondCheck.checkedByUsername,
        "User Name",
        secondCheck.checkedByUsername,
        calculationType = CalculationType.CALCULATED.name,
      ).right(),
    )
  }

  @ParameterizedTest
  @MethodSource("latestCalculationGenerators")
  fun `Should map CRDS additional fields excluding user display name if the user was not found`(
    name: String,
    getLatestCalculation: (prisonerId: String, latestCalculationService: LatestCalculationService) -> Either<String, LatestCalculation>,
  ) {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()
    val calcRequestId = 654321L
    val offenderKeyDates = OffenderKeyDates(
      sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      licenceExpiryDate = LocalDate.of(2025, 1, 2),
      conditionalReleaseDate = LocalDate.of(2025, 1, 7),
      reasonCode = "NEW",
      calculatedAt = calculatedAt,
      comment = "Some stuff and then the ref: $calculationReference",
      calculatedByUserId = "user1",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
    )

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)

    val calculationRequest = CalculationRequest(
      id = calcRequestId,
      calculationReference = calculationReference,
      calculatedAt = calculatedAt,
      reasonForCalculation = CalculationReason(0, false, false, "Some reason", false, null, null, null, false, false, false, null, isSecondCheck = false),
      calculatedByUsername = "username1",
    )
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.of(calculationRequest))

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED),
      ReleaseDate(LocalDate.of(2025, 1, 2), ReleaseDateType.LED),
      ReleaseDate(LocalDate.of(2025, 1, 7), ReleaseDateType.CRD),
    )
    whenever(offenderKeyDatesService.releaseDates(offenderKeyDates)).thenReturn(dates)
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null, null, null, null)).thenReturn(detailedDates.associateBy { it.type })
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    whenever(manageUsersApiClient.getUserByUsername("username")).thenReturn(null)
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(listOf(calculationRequest))
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))
    assertThat(getLatestCalculation(prisonerId, service)).describedAs(name).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        calculatedAt,
        null,
        654321,
        null,
        "Some reason",
        null,
        null,
        CalculationSource.CRDS,
        detailedDates,
        "username1",
        null,
        "User Name 1",
        null,
        calculationType = CalculationType.CALCULATED.name,
      ).right(),
    )
  }

  @ParameterizedTest
  @MethodSource("latestCalculationGenerators")
  fun `Should default to Not entered if reason for calc was not enabled on CRDS`(
    @Suppress("unused") name: String,
    getLatestCalculation: (prisonerId: String, latestCalculationService: LatestCalculationService) -> Either<String, LatestCalculation>,
  ) {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    val offenderKeyDates = OffenderKeyDates(
      conditionalReleaseDate = LocalDate.of(2025, 1, 7),
      reasonCode = "NEW",
      calculatedAt = calculatedAt,
      comment = "Some stuff and then the ref: $calculationReference",
      calculatedByUserId = "user1",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
    )
    val calculationRequest = CalculationRequest(
      id = 654321,
      calculationReference = calculationReference,
      calculatedAt = calculatedAt,
      reasonForCalculation = null,
    )
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.of(calculationRequest))
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(listOf(calculationRequest))
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 7), ReleaseDateType.CRD),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null, null, null, null)).thenReturn(detailedDates.associateBy { it.type })
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    assertThat(getLatestCalculation(prisonerId, service).getOrNull()!!.reason).isEqualTo("Not entered")
  }

  @ParameterizedTest
  @MethodSource("latestCalculationGenerators")
  fun `Should provide calculation reason further detail if there is some for the CRDS calc`(
    @Suppress("unused") name: String,
    getLatestCalculation: (prisonerId: String, latestCalculationService: LatestCalculationService) -> Either<String, LatestCalculation>,
  ) {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    val offenderKeyDates = OffenderKeyDates(
      conditionalReleaseDate = LocalDate.of(2025, 1, 7),
      reasonCode = "NEW",
      calculatedAt = calculatedAt,
      comment = "Some stuff and then the ref: $calculationReference",
      calculatedByUserId = "user1",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
    )
    val calculationRequest = CalculationRequest(
      id = 654321,
      calculationReference = calculationReference,
      calculatedAt = calculatedAt,
      reasonForCalculation = CalculationReason(0, false, true, "Other", false, null, null, null, false, false, true, null, isSecondCheck = false),
      otherReasonForCalculation = "Some further details",
    )
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.of(calculationRequest))
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(listOf(calculationRequest))
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 7), ReleaseDateType.CRD),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null, null, null, null)).thenReturn(detailedDates.associateBy { it.type })
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    val latestCalculation = getLatestCalculation(prisonerId, service).getOrNull()!!
    assertThat(latestCalculation.reason).isEqualTo("Other")
    assertThat(latestCalculation.reasonFurtherDetail).isEqualTo("Some further details")
  }

  @ParameterizedTest
  @MethodSource("latestCalculationGenerators")
  fun `Should lookup the location if there is one set on CRDS`(name: String, getLatestCalculation: (prisonerId: String, latestCalculationService: LatestCalculationService) -> Either<String, LatestCalculation>) {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "NEW",
      calculatedAt = calculatedAt,
      comment = "Some stuff and then the ref: $calculationReference",
      sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      calculatedByUserId = "user1",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
    )

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf(Agency("ABC", "HMP ABC")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)

    val calculationRequest = CalculationRequest(id = 654321, calculationReference = calculationReference, calculatedAt = calculatedAt, prisonerLocation = "ABC", calculatedByUsername = "username")
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(
      Optional.of(calculationRequest),
    )

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED),
    )
    whenever(offenderKeyDatesService.releaseDates(offenderKeyDates)).thenReturn(dates)
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null, null, null, null)).thenReturn(detailedDates.associateBy { it.type })
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(listOf(calculationRequest))
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))

    assertThat(getLatestCalculation(prisonerId, service)).describedAs(name).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        calculatedAt,
        null,
        654321,
        "HMP ABC",
        "Not entered",
        null,
        null,
        CalculationSource.CRDS,
        detailedDates,
        "username",
        null,
        "User Name",
        null,
        calculationType = CalculationType.CALCULATED.name,
      ).right(),
    )
  }

  @ParameterizedTest
  @MethodSource("latestCalculationGenerators")
  fun `Should default to location code if it's not in agency lookup`(name: String, getLatestCalculation: (prisonerId: String, latestCalculationService: LatestCalculationService) -> Either<String, LatestCalculation>) {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "NEW",
      calculatedAt = calculatedAt,
      comment = "Some stuff and then the ref: $calculationReference",
      sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      calculatedByUserId = "user1",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
    )

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf(Agency("ABC", "HMP ABC")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)

    val calculationRequest = CalculationRequest(id = 654321, calculationReference = calculationReference, calculatedAt = calculatedAt, prisonerLocation = "XYZ", calculatedByUsername = "username")
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.of(calculationRequest))
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(listOf(calculationRequest))
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))
    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(offenderKeyDatesService.releaseDates(offenderKeyDates)).thenReturn(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null, null, null, null)).thenReturn(detailedDates.associateBy { it.type })
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    assertThat(getLatestCalculation(prisonerId, service)).describedAs(name).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        calculatedAt,
        null,
        654321,
        "XYZ",
        "Not entered",
        null,
        null,
        CalculationSource.CRDS,
        detailedDates,
        "username",
        null,
        "User Name",
        null,
        calculationType = CalculationType.CALCULATED.name,
      ).right(),
    )
  }

  @ParameterizedTest
  @MethodSource("latestCalculationGenerators")
  fun `Should pass breakdown and sentences and offences for CRDS`(name: String, getLatestCalculation: (prisonerId: String, latestCalculationService: LatestCalculationService) -> Either<String, LatestCalculation>) {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "NEW",
      calculatedAt = calculatedAt,
      comment = "Some stuff and then the ref: $calculationReference",
      sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      calculatedByUserId = "user1",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
    )

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf(Agency("ABC", "HMP ABC")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())

    val calculationRequest = CalculationRequest(
      id = 654321,
      calculationReference = calculationReference,
      calculatedAt = calculatedAt,
      prisonerLocation = "ABC",
      sentenceAndOffences = objectToJson(listOf(someSentence), objectMapper),
      calculatedByUsername = "username",
    )
    val expectedBreakdown = CalculationBreakdown(emptyList(), null)
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.of(calculationRequest))
    whenever(calculationBreakdownService.getBreakdownSafely(calculationRequest)).thenReturn(expectedBreakdown.right())
    whenever(sourceDataMapper.mapSentencesAndOffences(calculationRequest)).thenReturn(listOf(someSentence))
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(listOf(calculationRequest))
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))

    val dates = listOf(ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED))
    whenever(offenderKeyDatesService.releaseDates(offenderKeyDates)).thenReturn(dates)
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, listOf(someSentence), expectedBreakdown, null, null, null)).thenReturn(detailedDates.associateBy { it.type })
    assertThat(getLatestCalculation(prisonerId, service)).describedAs(name).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        calculatedAt,
        null,
        654321,
        "HMP ABC",
        "Not entered",
        null,
        null,
        CalculationSource.CRDS,
        detailedDates,
        "username",
        null,
        "User Name",
        null,
        calculationType = CalculationType.CALCULATED.name,
      ).right(),
    )
  }

  @ParameterizedTest
  @MethodSource("latestCalculationGenerators")
  fun `Should not blow up if breakdown can't be generated for CRDS`(name: String, getLatestCalculation: (prisonerId: String, latestCalculationService: LatestCalculationService) -> Either<String, LatestCalculation>) {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "NEW",
      calculatedAt = calculatedAt,
      comment = "Some stuff and then the ref: $calculationReference",
      sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      calculatedByUserId = "user1",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
    )

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf(Agency("ABC", "HMP ABC")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())

    val calculationRequest = CalculationRequest(
      id = 654321,
      calculationReference = calculationReference,
      calculatedAt = calculatedAt,
      prisonerLocation = "ABC",
      sentenceAndOffences = objectToJson(listOf(someSentence), objectMapper),
      calculatedByUsername = "username",
    )
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.of(calculationRequest))
    whenever(calculationBreakdownService.getBreakdownSafely(calculationRequest)).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    whenever(sourceDataMapper.mapSentencesAndOffences(calculationRequest)).thenReturn(listOf(someSentence))
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(listOf(calculationRequest))
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))

    val dates = listOf(ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED))
    whenever(offenderKeyDatesService.releaseDates(offenderKeyDates)).thenReturn(dates)
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, listOf(someSentence), null, null, null, null)).thenReturn(detailedDates.associateBy { it.type })
    assertThat(getLatestCalculation(prisonerId, service)).describedAs(name).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        calculatedAt,
        null,
        654321,
        "HMP ABC",
        "Not entered",
        null,
        null,
        CalculationSource.CRDS,
        detailedDates,
        "username",
        null,
        "User Name",
        null,
        calculationType = CalculationType.CALCULATED.name,
      ).right(),
    )
  }

  @ParameterizedTest
  @MethodSource("latestCalculationGenerators")
  fun `Should not blow up if sentences and offences are missing for CRDS`(name: String, getLatestCalculation: (prisonerId: String, latestCalculationService: LatestCalculationService) -> Either<String, LatestCalculation>) {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()
    val offenderKeyDates = OffenderKeyDates(
      reasonCode = "NEW",
      calculatedAt = calculatedAt,
      comment = "Some stuff and then the ref: $calculationReference",
      sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      calculatedByUserId = "username",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
    )

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf(Agency("ABC", "HMP ABC")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)

    val calculationRequest = CalculationRequest(id = 654321, calculationReference = calculationReference, calculatedAt = calculatedAt, prisonerLocation = "ABC", sentenceAndOffences = null, calculatedByUsername = "username")
    val expectedBreakdown = CalculationBreakdown(emptyList(), null)
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.of(calculationRequest))
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(listOf(calculationRequest))
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))

    whenever(calculationBreakdownService.getBreakdownSafely(calculationRequest)).thenReturn(expectedBreakdown.right())

    val dates = listOf(ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED))
    whenever(offenderKeyDatesService.releaseDates(offenderKeyDates)).thenReturn(dates)
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, expectedBreakdown, null, null, null)).thenReturn(detailedDates.associateBy { it.type })
    assertThat(getLatestCalculation(prisonerId, service)).describedAs(name).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        calculatedAt,
        null,
        654321,
        "HMP ABC",
        "Not entered",
        null,
        null,
        CalculationSource.CRDS,
        detailedDates,
        "username",
        null,
        "User Name",
        null,
        calculationType = CalculationType.CALCULATED.name,
      ).right(),
    )
    verify(sourceDataMapper, never()).mapSentencesAndOffences(calculationRequest)
  }

  @ParameterizedTest
  @CsvSource(
    "10",
    "5",
    "3",
    "0",
  )
  fun `should map historic calculation limiting to the required number`(requestedNumberOfSummaries: Int) {
    val latestCalculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()
    val offenderKeyDates = OffenderKeyDates(
      sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      licenceExpiryDate = LocalDate.of(2025, 1, 2),
      conditionalReleaseDate = LocalDate.of(2025, 1, 7),
      reasonCode = "NEW",
      calculatedAt = calculatedAt,
      comment = "Some stuff and then the ref: $latestCalculationReference",
      calculatedByUserId = "username1",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
    )

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf(Agency("ABC", "HMP ABC")))
    whenever(prisonService.getNOMISCalcReasons()).thenReturn(listOf(NomisCalculationReason("NEW", "New Sentence")))

    val latestCalculationRequest = CalculationRequest(
      id = 654321,
      calculationReference = latestCalculationReference,
      calculatedAt = calculatedAt,
      reasonForCalculation = CalculationReason(0, false, false, "Some reason", false, null, null, null, false, false, false, null, isSecondCheck = false),
      calculatedByUsername = "username",
      prisonerLocation = "ABC",
    )
    val calculationRequest2 = CalculationRequest(
      id = 654321,
      calculationReference = UUID.randomUUID(),
      calculatedAt = calculatedAt.minusDays(1),
      reasonForCalculation = CalculationReason(0, false, false, "Reason 2", false, null, null, null, false, false, false, null, isSecondCheck = false),
      otherReasonForCalculation = "Some further details",
      calculatedByUsername = "username1",
    )
    val calculationRequest3 = CalculationRequest(
      id = 654321,
      calculationReference = UUID.randomUUID(),
      calculatedAt = calculatedAt.minusDays(2),
      reasonForCalculation = CalculationReason(0, false, false, "Reason 3", false, null, null, null, false, false, false, null, isSecondCheck = false),
      calculatedByUsername = "username1",
      genuineOverrideReason = GenuineOverrideReason.POWER_TO_DETAIN,
    )

    val latestNomisCalcSummary = SentenceCalculationSummary(
      bookingId = bookingId,
      offenderNo = prisonerId,
      firstName = prisonerDetails.firstName,
      lastName = prisonerDetails.lastName,
      agencyLocationId = "ABC",
      agencyDescription = "",
      offenderSentCalculationId = 1,
      calculationDate = offenderKeyDates.calculatedAt,
      staffId = 123,
      calculationReason = offenderKeyDates.reasonCode,
      calculatedByUserId = offenderKeyDates.calculatedByUserId,
      calculatedByFirstName = offenderKeyDates.calculatedByFirstName,
      calculatedByLastName = offenderKeyDates.calculatedByLastName,
      commentText = "Nomis Calc 1 ${latestCalculationRequest.calculationReference}",
    )
    val nomisCalcSummary2 = latestNomisCalcSummary.copy(calculationDate = calculatedAt.minusHours(12), commentText = "Nomis Calc 3", offenderSentCalculationId = 2)
    val nomisCalcSummary3 = latestNomisCalcSummary.copy(calculationDate = calculatedAt.minusDays(1), commentText = "Crds Calc 2: ${calculationRequest2.calculationReference}", offenderSentCalculationId = 3)
    val nomisCalcSummary4 = latestNomisCalcSummary.copy(calculationDate = calculatedAt.minusDays(1).minusHours(12), commentText = "Nomis Calc 3", offenderSentCalculationId = 4)
    val nomisCalcSummary5 = latestNomisCalcSummary.copy(calculationDate = calculatedAt.minusDays(2), commentText = "Crds Calc 3: ${calculationRequest3.calculationReference}", offenderSentCalculationId = 5)

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED),
      ReleaseDate(LocalDate.of(2025, 1, 2), ReleaseDateType.LED),
      ReleaseDate(LocalDate.of(2025, 1, 7), ReleaseDateType.CRD),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(offenderKeyDatesService.releaseDates(offenderKeyDates)).thenReturn(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null, null, null, null)).thenReturn(detailedDates.associateBy { it.type })
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(listOf(latestCalculationRequest, calculationRequest2, calculationRequest3))
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(latestNomisCalcSummary, nomisCalcSummary2, nomisCalcSummary3, nomisCalcSummary4, nomisCalcSummary5))

    val result = service.latestCalculationOverviewForPrisoner(prisonerId, requestedNumberOfSummaries).getOrNull()!!
    assertThat(result.latestCalculation).describedAs("latest calc set").isNotNull
    val expectedHistories = listOf(
      HistoricCalculationSummary(
        calculationDate = latestCalculationRequest.calculatedAt,
        calculationSource = CalculationSource.CRDS,
        calculationType = CalculationType.CALCULATED,
        crdsCalculationId = latestCalculationRequest.id(),
        nomisCalculationId = 1,
        reasonDescription = "Some reason",
        reasonFurtherDetail = null,
        genuineOverrideReasonDescription = null,
        calculatedByDisplayName = "User Name",
        establishmentCalculatedAtDescription = "HMP ABC",
      ),
      HistoricCalculationSummary(
        calculationDate = nomisCalcSummary2.calculationDate,
        calculationSource = CalculationSource.NOMIS,
        calculationType = null,
        crdsCalculationId = null,
        nomisCalculationId = 2,
        reasonDescription = "New Sentence",
        reasonFurtherDetail = null,
        genuineOverrideReasonDescription = null,
        calculatedByDisplayName = "User Name 1",
        establishmentCalculatedAtDescription = "",
      ),
      HistoricCalculationSummary(
        calculationDate = calculationRequest2.calculatedAt,
        calculationSource = CalculationSource.CRDS,
        calculationType = CalculationType.CALCULATED,
        crdsCalculationId = calculationRequest2.id(),
        nomisCalculationId = 3,
        reasonDescription = "Reason 2",
        reasonFurtherDetail = "Some further details",
        genuineOverrideReasonDescription = null,
        calculatedByDisplayName = "User Name 1",
        establishmentCalculatedAtDescription = null,
      ),
      HistoricCalculationSummary(
        calculationDate = nomisCalcSummary4.calculationDate,
        calculationSource = CalculationSource.NOMIS,
        calculationType = null,
        crdsCalculationId = null,
        nomisCalculationId = 4,
        reasonDescription = "New Sentence",
        reasonFurtherDetail = null,
        genuineOverrideReasonDescription = null,
        calculatedByDisplayName = "User Name 1",
        establishmentCalculatedAtDescription = "",
      ),
      HistoricCalculationSummary(
        calculationDate = calculationRequest3.calculatedAt,
        calculationSource = CalculationSource.CRDS,
        calculationType = CalculationType.CALCULATED,
        crdsCalculationId = calculationRequest3.id(),
        nomisCalculationId = 5,
        reasonDescription = "Reason 3",
        reasonFurtherDetail = null,
        genuineOverrideReasonDescription = "An application for a power to detain has been approved",
        calculatedByDisplayName = "User Name 1",
        establishmentCalculatedAtDescription = null,
      ),
    )
    assertThat(result.recentCalculations).hasSize(min(requestedNumberOfSummaries, 5)).containsExactly(*(expectedHistories.take(requestedNumberOfSummaries).toTypedArray()))
    assertThat(result.totalCalculationCount).isEqualTo(5)
  }

  @Test
  fun `Should map the number of sentences and any indeterminate sentences should set the flag`() {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()
    val calcRequestId = 654321L
    val offenderKeyDates = OffenderKeyDates(
      sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      licenceExpiryDate = LocalDate.of(2025, 1, 2),
      conditionalReleaseDate = LocalDate.of(2025, 1, 7),
      reasonCode = "NEW",
      calculatedAt = calculatedAt,
      comment = "Some stuff and then the ref: $calculationReference",
      calculatedByUserId = "user1",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
    )

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(offenderKeyDates.right())
    whenever(manageUsersApiClient.getUsersByUsernames(ArgumentMatchers.anySet())).thenReturn(usersDetails)

    val calculationRequest = CalculationRequest(
      id = calcRequestId,
      calculationReference = calculationReference,
      calculatedAt = calculatedAt,
      reasonForCalculation = CalculationReason(0, false, false, "Some reason", false, null, null, null, false, false, false, null, isSecondCheck = false),
      calculatedByUsername = "username1",
    )
    whenever(calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)).thenReturn(Optional.of(calculationRequest))

    whenever(offenderKeyDatesService.releaseDates(offenderKeyDates)).thenReturn(emptyList())
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(emptyList(), null, null, null, null, null)).thenReturn(emptyMap())
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    whenever(manageUsersApiClient.getUserByUsername("username")).thenReturn(null)
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)).thenReturn(listOf(calculationRequest))
    whenever(prisonService.getCalculationsForAPrisonerId(prisonerId)).thenReturn(listOf(summary(offenderKeyDates)))
    whenever(prisonService.getSentencesAndOffences(bookingId)).thenReturn(
      listOf(
        someSentence,
        someSentence.copy(sentenceCalculationType = SentenceCalculationType.DLP.name),
      ),
    )

    val result = service.latestCalculationOverviewForPrisoner(prisonerId, 0)

    assertThat(result.getOrNull()?.numberOfSentences).isEqualTo(2)
    assertThat(result.getOrNull()?.hasIndeterminateSentences).isTrue
  }

  private fun summary(offenderKeyDates: OffenderKeyDates): SentenceCalculationSummary = SentenceCalculationSummary(
    bookingId = bookingId,
    offenderNo = prisonerId,
    firstName = prisonerDetails.firstName,
    lastName = prisonerDetails.lastName,
    agencyLocationId = "ABC",
    agencyDescription = "",
    offenderSentCalculationId = 123456789,
    calculationDate = offenderKeyDates.calculatedAt,
    staffId = 123,
    calculationReason = offenderKeyDates.reasonCode,
    calculatedByUserId = offenderKeyDates.calculatedByUserId,
    calculatedByFirstName = offenderKeyDates.calculatedByFirstName,
    calculatedByLastName = offenderKeyDates.calculatedByLastName,
    commentText = offenderKeyDates.comment,
  )

  private fun calculationRequest(): CalculationRequest = CalculationRequest(1, reference, "123", 4565, CONFIRMED.name, calculatedAt = LocalDateTime.now(), prisonerLocation = "CDI")

  private fun secondCheckRecord(): CalculationRequestSecondCheck = CalculationRequestSecondCheck(1, calculationRequest().id(), "123", checkedByUsername = "username")

  private fun secondCheckRecordWithUnknownUsername(): CalculationRequestSecondCheck = CalculationRequestSecondCheck(1, calculationRequest().id(), "123", checkedByUsername = "username2")

  private val someSentence = SentenceAndOffenceWithReleaseArrangements(
    bookingId = 1L,
    sentenceSequence = 3,
    lineSequence = 2,
    caseSequence = 1,
    sentenceDate = ImportantDates.PCSC_COMMENCEMENT_DATE.minusDays(1),
    terms = listOf(
      SentenceTerms(years = 8),
    ),
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.ADIMP.name,
    sentenceTypeDescription = "ADMIP",
    offence = OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "ADIMP_ORA", "description", listOf("A")),
    caseReference = null,
    fineAmount = null,
    courtId = null,
    courtDescription = null,
    courtTypeCode = null,
    consecutiveToSequence = null,
    sdsReleaseArrangements = SDSReleaseArrangements(
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      sdsEarlyReleaseExclusions = emptyList(),
      isSection250 = false,
    ),
  )

  private fun toDetailedDates(dates: List<ReleaseDate>): List<DetailedDate> = dates.map { DetailedDate(it.type, it.type.description, it.date, emptyList()) }

  companion object {
    @JvmStatic
    fun latestCalculationGenerators(): Stream<Arguments> = Stream.of(
      Arguments.of("latest calc", { prisonerId: String, latestCalculationService: LatestCalculationService -> latestCalculationService.latestCalculationForPrisoner(prisonerId) }),
      Arguments.of("overview", { prisonerId: String, latestCalculationService: LatestCalculationService -> latestCalculationService.latestCalculationOverviewForPrisoner(prisonerId, 0).map { it.latestCalculation } }),
    )
  }
}
