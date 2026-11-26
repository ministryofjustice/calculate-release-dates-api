package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.test.web.reactive.server.expectBodyList
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculationTest.Companion.aFullyCompletedLegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculationTest.Companion.aMinimalLegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedCalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManuallyEnteredDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmittedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationToCrdsMappingResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.LegacySentenceCalculationRepository
import java.time.LocalDate
import java.time.LocalDateTime

@Sql(scripts = ["classpath:/test_data/reset-base-data.sql", "classpath:/test_data/load-base-data.sql"])
class LegacySentenceCalculationMigrationIntTest(private val mockPrisonService: MockPrisonService) : IntegrationTestBase() {
  @Autowired
  lateinit var legacySentenceCalculationRepository: LegacySentenceCalculationRepository

  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Autowired
  lateinit var calculationOutcomeRepository: CalculationOutcomeRepository

  @Autowired
  lateinit var approvedDatesSubmissionRepository: ApprovedDatesSubmissionRepository

  private val jsonTransformation = JsonTransformation()

  @BeforeEach
  fun setUp() {
    mockPrisonService.stubPostOffenderDates(PRISONER_ID.hashCode().toLong())
    mockPrisonService.withInstAgencies(
      listOf(Agency("ABC", "prison ABC")),
    )
    mockPrisonService.withNomisCalculationReasons(
      listOf(NomisCalculationReason("NEW", "New Sentence")),
    )
  }

  @Test
  fun `Can migrate legacy calculations for a prisoner with some matching CRDS calcs and some not but CRDS is the latest`() {
    val prelim = createPreliminaryCalculation(PRISONER_ID)
    val confirmed = createConfirmCalculationForPrisoner(prelim.calculationRequestId)

    val aNomisCalc = aMinimalLegacySentenceCalculationDto().copy(prisonerId = PRISONER_ID, calculationDate = LocalDateTime.of(2020, 1, 1, 10, 45))
    val aCrdsCalc = aFullyCompletedLegacySentenceCalculationDto().copy(prisonerId = PRISONER_ID, commentText = "CRDS: ${confirmed.calculationReference}", calculationDate = LocalDateTime.of(2020, 1, 2, 15, 30))
    val request = LegacySentenceCalculationMigrationRequest(listOf(aNomisCalc, aCrdsCalc))

    val response = migrateAPrisoner(request, PRISONER_ID)

    assertThat(response.mappingResults).containsExactlyInAnyOrder(
      LegacySentenceCalculationToCrdsMappingResult(aNomisCalc.nomisCalculationId, null),
      LegacySentenceCalculationToCrdsMappingResult(aCrdsCalc.nomisCalculationId, confirmed.calculationRequestId),
    )

    val legacySentenceCalculationsFromDb = legacySentenceCalculationRepository.getByPrisonerId(PRISONER_ID)
    assertThat(legacySentenceCalculationsFromDb).hasSize(2)
    assertThat(legacySentenceCalculationsFromDb.find { it.nomisCalculationId == aNomisCalc.nomisCalculationId }).isNotNull
    assertThat(legacySentenceCalculationsFromDb.find { it.nomisCalculationId == aCrdsCalc.nomisCalculationId }).isNotNull

    val legacySentenceCalculationFromReconciliationEndpoint = getAllByPrisonerId(PRISONER_ID)
    assertThat(legacySentenceCalculationFromReconciliationEndpoint).hasSize(2)
    assertThat(legacySentenceCalculationFromReconciliationEndpoint.find { it.nomisCalculationId == aNomisCalc.nomisCalculationId }).isNotNull
    assertThat(legacySentenceCalculationFromReconciliationEndpoint.find { it.nomisCalculationId == aCrdsCalc.nomisCalculationId }).isNotNull
  }

  @Test
  fun `Can migrate legacy calculations for a prisoner with some matching CRDS calcs and some not but NOMIS is the latest so a calculation request is created`() {
    val prelim = createPreliminaryCalculation(PRISONER_ID)
    val confirmed = createConfirmCalculationForPrisoner(prelim.calculationRequestId)

    val bookingId = PRISONER_ID.hashCode().toLong()

    val aNomisCalc = aFullyCompletedLegacySentenceCalculationDto().copy(
      nomisCalculationId = 745,
      prisonerId = PRISONER_ID,
      bookingId = bookingId,
      commentText = "Not CRDS.",
      calculationDate = LocalDateTime.of(2020, 1, 3, 10, 45),
      calculationReasonCode = "CORRECTN",
    )
    val aCrdsCalc = aFullyCompletedLegacySentenceCalculationDto().copy(
      nomisCalculationId = 845,
      prisonerId = PRISONER_ID,
      bookingId = bookingId,
      commentText = "CRDS: ${confirmed.calculationReference}",
      calculationDate = LocalDateTime.of(2020, 1, 2, 15, 30),
    )
    val request = LegacySentenceCalculationMigrationRequest(listOf(aNomisCalc, aCrdsCalc))

    val response = migrateAPrisoner(request, PRISONER_ID)

    val nomisCalcMappingResult = response.mappingResults.find { it.nomisCalculationId == aNomisCalc.nomisCalculationId }
    assertThat(nomisCalcMappingResult!!.crdsCalculationId).isNotNull
    assertThat(response.mappingResults.find { it.nomisCalculationId == aCrdsCalc.nomisCalculationId }).isEqualTo(LegacySentenceCalculationToCrdsMappingResult(aCrdsCalc.nomisCalculationId, confirmed.calculationRequestId))

    mockPrisonService.stubPrisoner(PrisonerDetails(bookingId = bookingId, offenderNo = CHANGED_PRISONER_ID, dateOfBirth = LocalDate.of(2000, 1, 1)))
    prisonApi.stubGetSentencesAndOffences(bookingId, jsonTransformation.getAllSentenceAndOffencesJson("1")[PRISONER_ID]!!)
    stubKeyDates(aCrdsCalc)

    val detailedReleaseDatesResponse = webTestClient.get()
      .uri("/calculation/detailed-results/${nomisCalcMappingResult.crdsCalculationId}")
      .accept(APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(APPLICATION_JSON)
      .expectBody<DetailedCalculationResults>()
      .returnResult().responseBody!!

    assertThat(detailedReleaseDatesResponse.dates).isNotEmpty

    val latestCalculation = webTestClient.get()
      .uri("/calculation/${PRISONER_ID}/latest")
      .accept(APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(APPLICATION_JSON)
      .expectBody<LatestCalculation>()
      .returnResult().responseBody!!
    assertThat(latestCalculation.dates).isNotNull

    val overview = getPrisonerOverview(PRISONER_ID)
    assertThat(overview.latestCalculation?.dates).isNotEmpty

    val calculationRequest = calculationRequestRepository.findById(nomisCalcMappingResult.crdsCalculationId!!).get()
    assertThat(calculationRequest.reasonForCalculation?.displayName).isEqualTo("Correcting an earlier sentence")
    assertThat(calculationRequest.otherReasonForCalculation).isNull()

    val calculationOutcomes = calculationOutcomeRepository.findByCalculationRequestId(nomisCalcMappingResult.crdsCalculationId!!)
    assertThat(calculationOutcomes).hasSize(19)
  }

  @Test
  fun `Creating a CRDS calc with no dates add None as a date type`() {
    val bookingId = PRISONER_ID.hashCode().toLong()

    val aNomisCalc = aMinimalLegacySentenceCalculationDto().copy(
      prisonerId = PRISONER_ID,
      bookingId = bookingId,
      calculationDate = LocalDateTime.of(2020, 1, 3, 10, 45),
    )
    val request = LegacySentenceCalculationMigrationRequest(listOf(aNomisCalc))

    val response = migrateAPrisoner(request, PRISONER_ID)

    val nomisCalcMappingResult = response.mappingResults.find { it.nomisCalculationId == aNomisCalc.nomisCalculationId }
    assertThat(nomisCalcMappingResult!!.crdsCalculationId).isNotNull

    mockPrisonService.stubPrisoner(PrisonerDetails(bookingId = bookingId, offenderNo = CHANGED_PRISONER_ID, dateOfBirth = LocalDate.of(2000, 1, 1)))
    prisonApi.stubGetSentencesAndOffences(bookingId, jsonTransformation.getAllSentenceAndOffencesJson("1")[PRISONER_ID]!!)
    stubKeyDates(aNomisCalc)

    webTestClient.get()
      .uri("/calculation/detailed-results/${nomisCalcMappingResult.crdsCalculationId}")
      .accept(APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk

    val calculationOutcomes = calculationOutcomeRepository.findByCalculationRequestId(nomisCalcMappingResult.crdsCalculationId!!)
    assertThat(calculationOutcomes).hasSize(1)
    assertThat(calculationOutcomes.first().calculationDateType).isEqualTo("None")
  }

  @Test
  fun `Creating a CRDS calc with a matching NOMIS reason with no equivalent to CRDS should use Other with the NOMIS description`() {
    val bookingId = PRISONER_ID.hashCode().toLong()

    val aNomisCalc = aMinimalLegacySentenceCalculationDto().copy(
      prisonerId = PRISONER_ID,
      bookingId = bookingId,
      calculationDate = LocalDateTime.of(2020, 1, 3, 10, 45),
      sedCalculatedDate = LocalDate.of(2019, 6, 10),
      calculationReasonCode = "PTD",
    )
    val request = LegacySentenceCalculationMigrationRequest(listOf(aNomisCalc))

    val response = migrateAPrisoner(request, PRISONER_ID)

    val nomisCalcMappingResult = response.mappingResults.find { it.nomisCalculationId == aNomisCalc.nomisCalculationId }
    assertThat(nomisCalcMappingResult!!.crdsCalculationId).isNotNull

    mockPrisonService.stubPrisoner(PrisonerDetails(bookingId = bookingId, offenderNo = CHANGED_PRISONER_ID, dateOfBirth = LocalDate.of(2000, 1, 1)))
    prisonApi.stubGetSentencesAndOffences(bookingId, jsonTransformation.getAllSentenceAndOffencesJson("1")[PRISONER_ID]!!)
    stubKeyDates(aNomisCalc)

    val overview = getPrisonerOverview(PRISONER_ID)
    assertThat(overview.latestCalculation?.dates).isNotEmpty

    val calculationRequest = calculationRequestRepository.findById(nomisCalcMappingResult.crdsCalculationId!!).get()
    assertThat(calculationRequest.reasonForCalculation?.displayName).isEqualTo("Other")
    assertThat(calculationRequest.otherReasonForCalculation).isEqualTo("S244ZB Power to Detain")
  }

  @Test
  fun `Creating a CRDS calc with no matching NOMIS reason should be allowed and map to OTHER with the code used as the description, this is probably bad data`() {
    val bookingId = PRISONER_ID.hashCode().toLong()

    val aNomisCalc = aMinimalLegacySentenceCalculationDto().copy(
      prisonerId = PRISONER_ID,
      bookingId = bookingId,
      calculationDate = LocalDateTime.of(2020, 1, 3, 10, 45),
      sedCalculatedDate = LocalDate.of(2019, 6, 10),
      calculationReasonCode = "blurg",
    )
    val request = LegacySentenceCalculationMigrationRequest(listOf(aNomisCalc))

    val response = migrateAPrisoner(request, PRISONER_ID)

    val nomisCalcMappingResult = response.mappingResults.find { it.nomisCalculationId == aNomisCalc.nomisCalculationId }
    assertThat(nomisCalcMappingResult!!.crdsCalculationId).isNotNull

    mockPrisonService.stubPrisoner(PrisonerDetails(bookingId = bookingId, offenderNo = CHANGED_PRISONER_ID, dateOfBirth = LocalDate.of(2000, 1, 1)))
    prisonApi.stubGetSentencesAndOffences(bookingId, jsonTransformation.getAllSentenceAndOffencesJson("1")[PRISONER_ID]!!)
    stubKeyDates(aNomisCalc)

    val overview = getPrisonerOverview(PRISONER_ID)
    assertThat(overview.latestCalculation?.dates).isNotEmpty

    val calculationRequest = calculationRequestRepository.findById(nomisCalcMappingResult.crdsCalculationId!!).get()
    assertThat(calculationRequest.reasonForCalculation?.displayName).isEqualTo("Other")
    assertThat(calculationRequest.otherReasonForCalculation).isEqualTo("blurg")
  }

  @Test
  fun `Can remigrate legacy calculations for a prisoner which should remove any existing ones`() {
    val prelim = createPreliminaryCalculation(PRISONER_ID)
    val confirmed = createConfirmCalculationForPrisoner(prelim.calculationRequestId)

    val aNomisCalc = aMinimalLegacySentenceCalculationDto().copy(prisonerId = PRISONER_ID, calculationDate = LocalDateTime.of(2020, 1, 1, 10, 45))
    val aCrdsCalc = aFullyCompletedLegacySentenceCalculationDto().copy(prisonerId = PRISONER_ID, commentText = "CRDS: ${confirmed.calculationReference}", calculationDate = LocalDateTime.of(2020, 1, 2, 15, 30))

    val firstRequest = LegacySentenceCalculationMigrationRequest(listOf(aNomisCalc, aCrdsCalc))

    val firstResponse = migrateAPrisoner(firstRequest, PRISONER_ID)

    assertThat(firstResponse.mappingResults).containsExactlyInAnyOrder(
      LegacySentenceCalculationToCrdsMappingResult(aNomisCalc.nomisCalculationId, null),
      LegacySentenceCalculationToCrdsMappingResult(aCrdsCalc.nomisCalculationId, confirmed.calculationRequestId),
    )

    val firstPassLegacySentenceCalculation = legacySentenceCalculationRepository.getByPrisonerId(PRISONER_ID)
    assertThat(firstPassLegacySentenceCalculation).hasSize(2)
    assertThat(firstPassLegacySentenceCalculation.find { it.nomisCalculationId == aNomisCalc.nomisCalculationId }).isNotNull
    assertThat(firstPassLegacySentenceCalculation.find { it.nomisCalculationId == aCrdsCalc.nomisCalculationId }).isNotNull

    val aChangedNomisCalc = aNomisCalc.copy(nomisCalculationId = 98, prisonerId = PRISONER_ID)
    val aChangedCrdsCalc = aCrdsCalc.copy(nomisCalculationId = 99, prisonerId = PRISONER_ID, commentText = "CRDS: ${confirmed.calculationReference}")
    val request = LegacySentenceCalculationMigrationRequest(listOf(aChangedNomisCalc, aChangedCrdsCalc))

    val secondResponse = migrateAPrisoner(request, PRISONER_ID)

    assertThat(secondResponse.mappingResults).containsExactlyInAnyOrder(
      LegacySentenceCalculationToCrdsMappingResult(aChangedNomisCalc.nomisCalculationId, null),
      LegacySentenceCalculationToCrdsMappingResult(aChangedCrdsCalc.nomisCalculationId, confirmed.calculationRequestId),
    )

    val secondPassLegacySentenceCalculation = legacySentenceCalculationRepository.getByPrisonerId(PRISONER_ID)
    assertThat(secondPassLegacySentenceCalculation).hasSize(2)

    // the original items should've been deleted and new ones retained
    assertThat(secondPassLegacySentenceCalculation.find { it.nomisCalculationId == aNomisCalc.nomisCalculationId }).isNull()
    assertThat(secondPassLegacySentenceCalculation.find { it.nomisCalculationId == aCrdsCalc.nomisCalculationId }).isNull()
    assertThat(secondPassLegacySentenceCalculation.find { it.nomisCalculationId == aChangedNomisCalc.nomisCalculationId }).isNotNull
    assertThat(secondPassLegacySentenceCalculation.find { it.nomisCalculationId == aChangedCrdsCalc.nomisCalculationId }).isNotNull
  }

  @Test
  fun `Migrating a sentence calc that has a different prisoner and booking will move the calculation request and booking id`() {
    val expectedNewBookingId = CHANGED_PRISONER_ID.hashCode().toLong()

    val prelim = createPreliminaryCalculation(PRISONER_ID)
    val confirmed = createConfirmCalculationForPrisoner(prelim.calculationRequestId, listOf(ManuallyEnteredDate(ReleaseDateType.ROTL, SubmittedDate(21, 6, 2027))))

    val aCrdsCalc = aFullyCompletedLegacySentenceCalculationDto().copy(prisonerId = CHANGED_PRISONER_ID, bookingId = expectedNewBookingId, commentText = "CRDS: ${confirmed.calculationReference}")
    val request = LegacySentenceCalculationMigrationRequest(listOf(aCrdsCalc))

    val response = migrateAPrisoner(request, CHANGED_PRISONER_ID)

    assertThat(response).isEqualTo(
      LegacySentenceCalculationMigrationResponse(
        listOf(
          LegacySentenceCalculationToCrdsMappingResult(aCrdsCalc.nomisCalculationId, confirmed.calculationRequestId),
        ),
      ),
    )

    val legacySentenceCalculation = legacySentenceCalculationRepository.getByPrisonerId(CHANGED_PRISONER_ID)
    assertThat(legacySentenceCalculation).hasSize(1)
    val persistedLegacySentenceCalc = legacySentenceCalculation.first()
    assertThat(persistedLegacySentenceCalc).isNotNull
    assertThat(persistedLegacySentenceCalc.calculationRequestId).isEqualTo(confirmed.calculationRequestId)
    assertThat(persistedLegacySentenceCalc.prisonerId).isEqualTo(CHANGED_PRISONER_ID)
    assertThat(persistedLegacySentenceCalc.bookingId).isEqualTo(expectedNewBookingId)

    mockPrisonService.stubPrisoner(PrisonerDetails(bookingId = expectedNewBookingId, offenderNo = CHANGED_PRISONER_ID, dateOfBirth = LocalDate.of(2000, 1, 1)))
    prisonApi.stubGetSentencesAndOffences(expectedNewBookingId, jsonTransformation.getAllSentenceAndOffencesJson("1")[PRISONER_ID]!!)
    stubKeyDates(aCrdsCalc)
    val latestCalc = getPrisonerOverview(CHANGED_PRISONER_ID)
    assertThat(latestCalc.latestCalculation?.source).describedAs("Mapped as CRDS if prisoner and booking moved correctly").isEqualTo(CalculationSource.CRDS)
    assertThat(latestCalc.latestCalculation?.calculationRequestId).isEqualTo(confirmed.calculationRequestId)

    val persistedCalculationRequest = calculationRequestRepository.findById(confirmed.calculationRequestId).get()
    assertThat(persistedCalculationRequest.prisonerId).isEqualTo(CHANGED_PRISONER_ID)
    val persistedApprovedDates = approvedDatesSubmissionRepository.findByCalculationRequestId(confirmed.calculationRequestId)
    assertThat(persistedApprovedDates?.prisonerId).isEqualTo(CHANGED_PRISONER_ID)
  }

  private fun stubKeyDates(calc: LegacySentenceCalculationDto) {
    mockPrisonService.stubHistoricCalculations(
      calc.prisonerId,
      listOf(
        SentenceCalculationSummary(
          bookingId = calc.bookingId,
          offenderNo = calc.prisonerId,
          firstName = "Bob",
          lastName = "Smith",
          agencyLocationId = "ABC",
          agencyDescription = "Some agency description from NOMIS",
          offenderSentCalculationId = 987654321,
          calculationDate = LocalDateTime.now(),
          staffId = 321,
          calculationReason = "NEW",
          calculatedByUserId = "user1",
          commentText = calc.commentText,
        ),
      ),
    )
    mockPrisonService.stubKeyDates(
      calc.bookingId,
      OffenderKeyDates(
        "NEW",
        LocalDateTime.now(),
        calc.commentText,
        conditionalReleaseDate = LocalDate.of(2016, 1, 6),
        topupSupervisionExpiryDate = LocalDate.of(2017, 1, 6),
        homeDetentionCurfewEligibilityDate = LocalDate.of(2015, 8, 7),
        effectiveSentenceEndDate = LocalDate.of(2016, 11, 16),
        sentenceExpiryDate = LocalDate.of(2016, 11, 6),
        licenceExpiryDate = LocalDate.of(2016, 11, 6),
        calculatedByUserId = "test-client",
      ),
    )
  }

  private fun getAllByPrisonerId(prisonerId: String) = webTestClient.get()
    .uri("/legacy-sentence-calculation/$prisonerId")
    .accept(APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_CALCULATE_RELEASE_DATES__LEGACY_SENTENCE_CALCULATION__RO")))
    .exchange()
    .expectStatus().isOk
    .expectBodyList<LegacySentenceCalculationDto>()
    .returnResult().responseBody!!

  companion object {
    private const val PRISONER_ID = "default"
    private const val CHANGED_PRISONER_ID = "otherpris"
  }
}
