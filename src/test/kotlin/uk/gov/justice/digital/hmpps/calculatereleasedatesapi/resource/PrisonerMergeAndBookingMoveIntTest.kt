package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.dbhelper.TestTransactionalService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculationTest.Companion.aMinimalLegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.AdjustmentsApiExtension.Companion.adjustmentsApi
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.PrisonApiExtension.Companion.DEFAULT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManuallyEnteredDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerModifiedReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmittedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.LegacySentenceCalculationRepository
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["feature-toggles.move-and-merge-events-enabled=true"])
@Sql(scripts = ["classpath:/test_data/reset-base-data.sql"])
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
class PrisonerMergeAndBookingMoveIntTest(private val mockPrisonService: MockPrisonService) : SqsIntegrationTestBase() {
  @Autowired
  lateinit var testTransactionalService: TestTransactionalService

  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Autowired
  lateinit var approvedDatesSubmissionRepository: ApprovedDatesSubmissionRepository

  @Autowired
  lateinit var legacySentenceCalculationRepository: LegacySentenceCalculationRepository

  private val jsonTransformation = JsonTransformation()

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

    val sentences = jsonTransformation.getAllSentenceAndOffencesJson("1")
    val movements = jsonTransformation.getAllExternalMovementsJson()

    listOf(PRISONER_ONE_OLD_BOOKING_ID, PRISONER_TWO_OLD_BOOKING_ID, BOOKING_ID_TO_MOVE).onEach {
      prisonApi.stubGetSentencesAndOffences(it, sentences[DEFAULT]!!)
      mockPrisonService.stubPostOffenderDates(it)
    }
    listOf(PRISONER_ONE_ID, PRISONER_TWO_ID).onEach {
      adjustmentsApi.stubGetAdjustments(it, "[]")
      prisonApi.stubExternalMovements(it, movements[DEFAULT]!!)
    }
  }

  @Test
  fun `Should move calculations only for the specified booking id from one prisoner to another for booking move event`() {
    // initial calculation with prisoner 1 and prisoner 2
    mockPrisonService.stubPrisoner(PrisonerDetails(bookingId = PRISONER_ONE_OLD_BOOKING_ID, offenderNo = PRISONER_ONE_ID, dateOfBirth = LocalDate.of(2000, 1, 1)))
    mockPrisonService.stubPrisoner(PrisonerDetails(bookingId = PRISONER_TWO_OLD_BOOKING_ID, offenderNo = PRISONER_TWO_ID, dateOfBirth = LocalDate.of(2000, 1, 1)))

    // create a calc on old booking id for prisoner one
    val prelimPrisonerOneOldBookingId = createPreliminaryCalculation(PRISONER_ONE_ID)
    val confirmedPrisonerOneOldBookingId = createConfirmCalculationForPrisoner(prelimPrisonerOneOldBookingId.calculationRequestId)
    stubKeyDates(PRISONER_ONE_ID, PRISONER_ONE_OLD_BOOKING_ID, confirmedPrisonerOneOldBookingId)
    val latestCalculationPrisonerOneBeforeNewBooking = getPrisonerOverview(PRISONER_ONE_ID)
    assertThat(latestCalculationPrisonerOneBeforeNewBooking.latestCalculation?.bookingId).isEqualTo(PRISONER_ONE_OLD_BOOKING_ID)
    assertThat(latestCalculationPrisonerOneBeforeNewBooking.latestCalculation?.calculationRequestId).isEqualTo(confirmedPrisonerOneOldBookingId.calculationRequestId)

    // create a calc on old booking id for prisoner two
    val prelimPrisonerTwoOldBookingId = createPreliminaryCalculation(PRISONER_TWO_ID)
    val confirmedPrisonerTwoOldBookingId = createConfirmCalculationForPrisoner(prelimPrisonerTwoOldBookingId.calculationRequestId)
    stubKeyDates(PRISONER_TWO_ID, PRISONER_TWO_OLD_BOOKING_ID, confirmedPrisonerTwoOldBookingId)
    val latestCalculationPrisonerTwoBeforeNewBooking = getPrisonerOverview(PRISONER_TWO_ID)
    assertThat(latestCalculationPrisonerTwoBeforeNewBooking.latestCalculation?.bookingId).isEqualTo(PRISONER_TWO_OLD_BOOKING_ID)
    assertThat(latestCalculationPrisonerTwoBeforeNewBooking.latestCalculation?.calculationRequestId).isEqualTo(confirmedPrisonerTwoOldBookingId.calculationRequestId)

    // now change prisoner 1 to a new booking id and calculate again
    mockPrisonService.stubPrisoner(PrisonerDetails(bookingId = BOOKING_ID_TO_MOVE, offenderNo = PRISONER_ONE_ID, dateOfBirth = LocalDate.of(2000, 1, 1)))

    val prelimPrisonerOneNewBookingId = createPreliminaryCalculation(PRISONER_ONE_ID)
    val confirmedPrisonerOneNewBookingId = createConfirmCalculationForPrisoner(prelimPrisonerOneNewBookingId.calculationRequestId, listOf(ManuallyEnteredDate(ReleaseDateType.ROTL, SubmittedDate(21, 6, 1998))))
    stubKeyDates(PRISONER_ONE_ID, BOOKING_ID_TO_MOVE, confirmedPrisonerOneNewBookingId)
    val latestCalculationPrisonerOneAfterNewBooking = getPrisonerOverview(PRISONER_ONE_ID)
    assertThat(latestCalculationPrisonerOneAfterNewBooking.latestCalculation?.bookingId).isEqualTo(BOOKING_ID_TO_MOVE)
    assertThat(latestCalculationPrisonerOneAfterNewBooking.latestCalculation?.calculationRequestId).isEqualTo(confirmedPrisonerOneNewBookingId.calculationRequestId)

    // import all the legacy sentence calcs to ensure they are also moved
    migrateAPrisoner(
      LegacySentenceCalculationMigrationRequest(
        listOf(
          aMinimalLegacySentenceCalculationDto().copy(nomisCalculationId = 199, prisonerId = PRISONER_ONE_ID, bookingId = PRISONER_ONE_OLD_BOOKING_ID, commentText = "CRDS calc: ${confirmedPrisonerOneOldBookingId.calculationReference}"),
          aMinimalLegacySentenceCalculationDto().copy(nomisCalculationId = 200, prisonerId = PRISONER_ONE_ID, bookingId = BOOKING_ID_TO_MOVE, commentText = "CRDS calc: ${confirmedPrisonerOneNewBookingId.calculationReference}"),
        ),
      ),
      PRISONER_ONE_ID,
    )
    migrateAPrisoner(
      LegacySentenceCalculationMigrationRequest(
        listOf(
          aMinimalLegacySentenceCalculationDto().copy(nomisCalculationId = 201, prisonerId = PRISONER_TWO_ID, bookingId = PRISONER_TWO_OLD_BOOKING_ID, commentText = "CRDS calc: ${confirmedPrisonerTwoOldBookingId.calculationReference}"),
        ),
      ),
      PRISONER_TWO_ID,
    )

    // now simulate a move in NOMIS by changing the new booking id from prisoner one to prisoner two and generate a move event
    mockPrisonService.stubPrisoner(PrisonerDetails(bookingId = PRISONER_ONE_OLD_BOOKING_ID, offenderNo = PRISONER_ONE_ID, dateOfBirth = LocalDate.of(2000, 1, 1)))
    mockPrisonService.stubPrisoner(PrisonerDetails(bookingId = BOOKING_ID_TO_MOVE, offenderNo = PRISONER_TWO_ID, dateOfBirth = LocalDate.of(2000, 1, 1)))

    cleanQueues()
    publishBookingMoveEvent(BOOKING_ID_TO_MOVE, PRISONER_ONE_ID, PRISONER_TWO_ID)
    waitForAllCalculationEventsToBePublished(PRISONER_ONE_ID, PRISONER_TWO_ID)

    stubKeyDates(PRISONER_ONE_ID, PRISONER_ONE_OLD_BOOKING_ID, confirmedPrisonerOneOldBookingId)
    stubKeyDates(PRISONER_TWO_ID, BOOKING_ID_TO_MOVE, confirmedPrisonerOneNewBookingId)
    val latestCalculationPrisonerOneAfterMove = getPrisonerOverview(PRISONER_ONE_ID)
    val latestCalculationPrisonerTwoAfterMove = getPrisonerOverview(PRISONER_TWO_ID)

    // should be old calculation performed originally before the new booking id
    assertThat(latestCalculationPrisonerOneAfterMove.latestCalculation?.bookingId).isEqualTo(PRISONER_ONE_OLD_BOOKING_ID)
    assertThat(latestCalculationPrisonerOneAfterMove.latestCalculation?.calculationRequestId).isEqualTo(confirmedPrisonerOneOldBookingId.calculationRequestId)
    assertThat(latestCalculationPrisonerOneAfterMove.latestCalculation?.source)
      .describedAs("source should be CRDS if calculation request moved correctly on prisoner 1")
      .isEqualTo(CalculationSource.CRDS)

    // should be new calculation performed on the new booking id moved from prisoner 1 to 2
    assertThat(latestCalculationPrisonerTwoAfterMove.latestCalculation?.bookingId).isEqualTo(BOOKING_ID_TO_MOVE)
    assertThat(latestCalculationPrisonerTwoAfterMove.latestCalculation?.calculationRequestId).isEqualTo(confirmedPrisonerOneNewBookingId.calculationRequestId)
    assertThat(latestCalculationPrisonerTwoAfterMove.latestCalculation?.source)
      .describedAs("source should be CRDS if calculation request moved correctly on prisoner 2")
      .isEqualTo(CalculationSource.CRDS)

    // should have marked prelim calculation request, confirmed calc and approved dates submissions as moved
    calculationRequestRepository.findById(prelimPrisonerOneNewBookingId.calculationRequestId).orElseThrow().let { calcRequest ->
      assertThat(calcRequest.prisonerId).isEqualTo(PRISONER_TWO_ID)
      assertThat(calcRequest.bookingId).isEqualTo(BOOKING_ID_TO_MOVE)
      assertThat(calcRequest.prisonerModifiedReason).isEqualTo(PrisonerModifiedReason.BOOKING_MOVE)
      assertThat(calcRequest.prisonerModifiedPreviousPrisonerId).isEqualTo(PRISONER_ONE_ID)
    }
    calculationRequestRepository.findById(confirmedPrisonerOneNewBookingId.calculationRequestId).orElseThrow().let { calcRequest ->
      assertThat(calcRequest.prisonerId).isEqualTo(PRISONER_TWO_ID)
      assertThat(calcRequest.bookingId).isEqualTo(BOOKING_ID_TO_MOVE)
      assertThat(calcRequest.prisonerModifiedReason).isEqualTo(PrisonerModifiedReason.BOOKING_MOVE)
      assertThat(calcRequest.prisonerModifiedPreviousPrisonerId).isEqualTo(PRISONER_ONE_ID)
    }
    approvedDatesSubmissionRepository.findByCalculationRequestId(confirmedPrisonerOneNewBookingId.calculationRequestId)!!.let { approvedDateSubmission ->
      assertThat(approvedDateSubmission.prisonerId).isEqualTo(PRISONER_TWO_ID)
      assertThat(approvedDateSubmission.bookingId).isEqualTo(BOOKING_ID_TO_MOVE)
      assertThat(approvedDateSubmission.prisonerModifiedReason).isEqualTo(PrisonerModifiedReason.BOOKING_MOVE)
      assertThat(approvedDateSubmission.prisonerModifiedPreviousPrisonerId).isEqualTo(PRISONER_ONE_ID)
    }
    // legacy sentences for prisoner one should only contain the original booking id and for prisoner two should have both
    legacySentenceCalculationRepository.getByPrisonerId(PRISONER_ONE_ID).let { legacyCalcs ->
      assertThat(legacyCalcs).hasSize(1)
      assertThat(legacyCalcs.first().calculationRequestId).isEqualTo(confirmedPrisonerOneOldBookingId.calculationRequestId)
    }
    legacySentenceCalculationRepository.getByPrisonerId(PRISONER_TWO_ID).let { legacyCalcs ->
      assertThat(legacyCalcs).hasSize(2)
      assertThat(legacyCalcs.find { it.calculationRequestId == confirmedPrisonerOneNewBookingId.calculationRequestId }).isNotNull
      assertThat(legacyCalcs.find { it.calculationRequestId == confirmedPrisonerTwoOldBookingId.calculationRequestId }).isNotNull
    }
  }

  @Test
  fun `Should move all calculations from one prisoner to another on a prisoner merge event`() {
    // initial calculation with prisoner 1 and prisoner 2
    mockPrisonService.stubPrisoner(PrisonerDetails(bookingId = PRISONER_ONE_OLD_BOOKING_ID, offenderNo = PRISONER_ONE_ID, dateOfBirth = LocalDate.of(2000, 1, 1)))
    mockPrisonService.stubPrisoner(PrisonerDetails(bookingId = PRISONER_TWO_OLD_BOOKING_ID, offenderNo = PRISONER_TWO_ID, dateOfBirth = LocalDate.of(2000, 1, 1)))

    // create a calc on old booking id for prisoner one
    val prelimPrisonerOneOldBookingId = createPreliminaryCalculation(PRISONER_ONE_ID)
    val confirmedPrisonerOneOldBookingId = createConfirmCalculationForPrisoner(prelimPrisonerOneOldBookingId.calculationRequestId)
    stubKeyDates(PRISONER_ONE_ID, PRISONER_ONE_OLD_BOOKING_ID, confirmedPrisonerOneOldBookingId)
    val latestCalculationPrisonerOneBeforeNewBooking = getPrisonerOverview(PRISONER_ONE_ID)
    assertThat(latestCalculationPrisonerOneBeforeNewBooking.latestCalculation?.bookingId).isEqualTo(PRISONER_ONE_OLD_BOOKING_ID)
    assertThat(latestCalculationPrisonerOneBeforeNewBooking.latestCalculation?.calculationRequestId).isEqualTo(confirmedPrisonerOneOldBookingId.calculationRequestId)

    // create a calc on old booking id for prisoner two
    val prelimPrisonerTwoOldBookingId = createPreliminaryCalculation(PRISONER_TWO_ID)
    val confirmedPrisonerTwoOldBookingId = createConfirmCalculationForPrisoner(prelimPrisonerTwoOldBookingId.calculationRequestId)
    stubKeyDates(PRISONER_TWO_ID, PRISONER_TWO_OLD_BOOKING_ID, confirmedPrisonerTwoOldBookingId)
    val latestCalculationPrisonerTwoBeforeNewBooking = getPrisonerOverview(PRISONER_TWO_ID)
    assertThat(latestCalculationPrisonerTwoBeforeNewBooking.latestCalculation?.bookingId).isEqualTo(PRISONER_TWO_OLD_BOOKING_ID)
    assertThat(latestCalculationPrisonerTwoBeforeNewBooking.latestCalculation?.calculationRequestId).isEqualTo(confirmedPrisonerTwoOldBookingId.calculationRequestId)

    // now change prisoner 1 to a new booking id and calculate again
    mockPrisonService.stubPrisoner(PrisonerDetails(bookingId = BOOKING_ID_TO_MOVE, offenderNo = PRISONER_ONE_ID, dateOfBirth = LocalDate.of(2000, 1, 1)))

    val prelimPrisonerOneNewBookingId = createPreliminaryCalculation(PRISONER_ONE_ID)
    val confirmedPrisonerOneNewBookingId = createConfirmCalculationForPrisoner(prelimPrisonerOneNewBookingId.calculationRequestId, listOf(ManuallyEnteredDate(ReleaseDateType.ROTL, SubmittedDate(21, 6, 1998))))
    stubKeyDates(PRISONER_ONE_ID, BOOKING_ID_TO_MOVE, confirmedPrisonerOneNewBookingId)
    val latestCalculationPrisonerOneAfterNewBooking = getPrisonerOverview(PRISONER_ONE_ID)
    assertThat(latestCalculationPrisonerOneAfterNewBooking.latestCalculation?.bookingId).isEqualTo(BOOKING_ID_TO_MOVE)
    assertThat(latestCalculationPrisonerOneAfterNewBooking.latestCalculation?.calculationRequestId).isEqualTo(confirmedPrisonerOneNewBookingId.calculationRequestId)

    // import all the legacy sentence calcs to ensure they are also moved
    migrateAPrisoner(
      LegacySentenceCalculationMigrationRequest(
        listOf(
          aMinimalLegacySentenceCalculationDto().copy(nomisCalculationId = 199, prisonerId = PRISONER_ONE_ID, bookingId = PRISONER_ONE_OLD_BOOKING_ID, commentText = "CRDS calc: ${confirmedPrisonerOneOldBookingId.calculationReference}"),
          aMinimalLegacySentenceCalculationDto().copy(nomisCalculationId = 200, prisonerId = PRISONER_ONE_ID, bookingId = BOOKING_ID_TO_MOVE, commentText = "CRDS calc: ${confirmedPrisonerOneNewBookingId.calculationReference}"),
        ),
      ),
      PRISONER_ONE_ID,
    )
    migrateAPrisoner(
      LegacySentenceCalculationMigrationRequest(
        listOf(
          aMinimalLegacySentenceCalculationDto().copy(nomisCalculationId = 201, prisonerId = PRISONER_TWO_ID, bookingId = PRISONER_TWO_OLD_BOOKING_ID, commentText = "CRDS calc: ${confirmedPrisonerTwoOldBookingId.calculationReference}"),
        ),
      ),
      PRISONER_TWO_ID,
    )

    // now simulate a merge of prisoner one into prisoner two in NOMIS by changing the new booking id from prisoner one to prisoner two with prisoner one not returning not found
    mockPrisonService.stubPrisonerNotFound(PRISONER_ONE_ID)
    mockPrisonService.stubPrisoner(PrisonerDetails(bookingId = BOOKING_ID_TO_MOVE, offenderNo = PRISONER_TWO_ID, dateOfBirth = LocalDate.of(2000, 1, 1)))

    cleanQueues()
    publishPrisonerMergeEvent(PRISONER_TWO_ID, PRISONER_ONE_ID)
    waitForAllCalculationEventsToBePublished(PRISONER_ONE_ID, PRISONER_TWO_ID)

    stubKeyDates(PRISONER_TWO_ID, BOOKING_ID_TO_MOVE, confirmedPrisonerOneNewBookingId)
    webTestClient.get()
      .uri("/calculation/$PRISONER_ONE_ID/overview")
      .accept(APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("CALCULATE_RELEASE_DATES__CALCULATE__RO")))
      .exchange()
      .expectStatus().isNotFound

    val latestCalculationPrisonerTwoAfterMove = getPrisonerOverview(PRISONER_TWO_ID)

    // should be new calculation performed on the new booking id moved from prisoner 1 to 2
    assertThat(latestCalculationPrisonerTwoAfterMove.latestCalculation?.bookingId).isEqualTo(BOOKING_ID_TO_MOVE)
    assertThat(latestCalculationPrisonerTwoAfterMove.latestCalculation?.calculationRequestId).isEqualTo(confirmedPrisonerOneNewBookingId.calculationRequestId)
    assertThat(latestCalculationPrisonerTwoAfterMove.latestCalculation?.source)
      .describedAs("source should be CRDS if calculation request moved correctly on prisoner 2")
      .isEqualTo(CalculationSource.CRDS)

    // should have marked all calculation requests and approved dates submissions as moved
    calculationRequestRepository.findById(prelimPrisonerOneOldBookingId.calculationRequestId).orElseThrow().let { calcRequest ->
      assertThat(calcRequest.prisonerId).isEqualTo(PRISONER_TWO_ID)
      assertThat(calcRequest.bookingId).isEqualTo(PRISONER_ONE_OLD_BOOKING_ID)
      assertThat(calcRequest.prisonerModifiedReason).isEqualTo(PrisonerModifiedReason.PRISONER_MERGE)
      assertThat(calcRequest.prisonerModifiedPreviousPrisonerId).isEqualTo(PRISONER_ONE_ID)
    }
    calculationRequestRepository.findById(confirmedPrisonerOneOldBookingId.calculationRequestId).orElseThrow().let { calcRequest ->
      assertThat(calcRequest.prisonerId).isEqualTo(PRISONER_TWO_ID)
      assertThat(calcRequest.bookingId).isEqualTo(PRISONER_ONE_OLD_BOOKING_ID)
      assertThat(calcRequest.prisonerModifiedReason).isEqualTo(PrisonerModifiedReason.PRISONER_MERGE)
      assertThat(calcRequest.prisonerModifiedPreviousPrisonerId).isEqualTo(PRISONER_ONE_ID)
    }
    calculationRequestRepository.findById(prelimPrisonerOneNewBookingId.calculationRequestId).orElseThrow().let { calcRequest ->
      assertThat(calcRequest.prisonerId).isEqualTo(PRISONER_TWO_ID)
      assertThat(calcRequest.bookingId).isEqualTo(BOOKING_ID_TO_MOVE)
      assertThat(calcRequest.prisonerModifiedReason).isEqualTo(PrisonerModifiedReason.PRISONER_MERGE)
      assertThat(calcRequest.prisonerModifiedPreviousPrisonerId).isEqualTo(PRISONER_ONE_ID)
    }
    calculationRequestRepository.findById(confirmedPrisonerOneNewBookingId.calculationRequestId).orElseThrow().let { calcRequest ->
      assertThat(calcRequest.prisonerId).isEqualTo(PRISONER_TWO_ID)
      assertThat(calcRequest.bookingId).isEqualTo(BOOKING_ID_TO_MOVE)
      assertThat(calcRequest.prisonerModifiedReason).isEqualTo(PrisonerModifiedReason.PRISONER_MERGE)
      assertThat(calcRequest.prisonerModifiedPreviousPrisonerId).isEqualTo(PRISONER_ONE_ID)
    }
    approvedDatesSubmissionRepository.findByCalculationRequestId(confirmedPrisonerOneNewBookingId.calculationRequestId)!!.let { approvedDateSubmission ->
      assertThat(approvedDateSubmission.prisonerId).isEqualTo(PRISONER_TWO_ID)
      assertThat(approvedDateSubmission.bookingId).isEqualTo(BOOKING_ID_TO_MOVE)
      assertThat(approvedDateSubmission.prisonerModifiedReason).isEqualTo(PrisonerModifiedReason.PRISONER_MERGE)
      assertThat(approvedDateSubmission.prisonerModifiedPreviousPrisonerId).isEqualTo(PRISONER_ONE_ID)
    }

    // legacy sentences for prisoner one should all be moved to the new prisoner id
    legacySentenceCalculationRepository.getByPrisonerId(PRISONER_ONE_ID).let { legacyCalcs ->
      assertThat(legacyCalcs).isEmpty()
    }
    legacySentenceCalculationRepository.getByPrisonerId(PRISONER_TWO_ID).let { legacyCalcs ->
      assertThat(legacyCalcs).hasSize(3)
      assertThat(legacyCalcs.find { it.calculationRequestId == confirmedPrisonerOneOldBookingId.calculationRequestId }).isNotNull
      assertThat(legacyCalcs.find { it.calculationRequestId == confirmedPrisonerOneNewBookingId.calculationRequestId }).isNotNull
      assertThat(legacyCalcs.find { it.calculationRequestId == confirmedPrisonerTwoOldBookingId.calculationRequestId }).isNotNull
    }
  }

  private fun stubKeyDates(prisonerId: String, bookingId: Long, calc: CalculatedReleaseDates) {
    mockPrisonService.stubHistoricCalculations(
      prisonerId,
      listOf(
        SentenceCalculationSummary(
          bookingId = bookingId,
          offenderNo = prisonerId,
          firstName = "Bob",
          lastName = "Smith",
          agencyLocationId = "ABC",
          agencyDescription = "Some agency description from NOMIS",
          offenderSentCalculationId = 987654321,
          calculationDate = LocalDateTime.now(),
          staffId = 321,
          calculationReason = "NEW",
          calculatedByUserId = "user1",
          calculatedByFirstName = "User",
          calculatedByLastName = "Name",
          commentText = "CRDS calc ${calc.calculationReference}",
        ),
      ),
    )
    mockPrisonService.stubKeyDates(
      bookingId,
      OffenderKeyDates(
        "NEW",
        LocalDateTime.now(),
        "From CRDS: ${calc.calculationReference}",
        conditionalReleaseDate = LocalDate.of(2016, 1, 6),
        topupSupervisionExpiryDate = LocalDate.of(2017, 1, 6),
        homeDetentionCurfewEligibilityDate = LocalDate.of(2015, 8, 7),
        effectiveSentenceEndDate = LocalDate.of(2016, 11, 16),
        sentenceExpiryDate = LocalDate.of(2016, 11, 6),
        licenceExpiryDate = LocalDate.of(2016, 11, 6),
        calculatedByUserId = "test-client",
        calculatedByFirstName = "User",
        calculatedByLastName = "One",
      ),
    )
  }

  private fun migrateAPrisoner(request: LegacySentenceCalculationMigrationRequest, prisonerId: String) = webTestClient.post()
    .uri("/legacy-data/migrate/$prisonerId")
    .accept(APPLICATION_JSON)
    .contentType(APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .bodyValue(request)
    .exchange()
    .expectStatus().isOk
    .expectBody<LegacySentenceCalculationMigrationResponse>()
    .returnResult().responseBody!!

  companion object {
    const val PRISONER_ONE_ID = "pris01"
    const val PRISONER_TWO_ID = "default"

    private const val PRISONER_ONE_OLD_BOOKING_ID = 1L
    private const val BOOKING_ID_TO_MOVE = 2L
    private const val PRISONER_TWO_OLD_BOOKING_ID = 3L
  }
}
