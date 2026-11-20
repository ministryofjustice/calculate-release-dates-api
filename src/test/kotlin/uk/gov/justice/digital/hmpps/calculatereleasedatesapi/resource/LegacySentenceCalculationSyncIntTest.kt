package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculationTest.Companion.aMinimalLegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManuallyEnteredDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmittedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationToCrdsMappingResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.LegacySentenceCalculationRepository

@Sql(scripts = ["classpath:/test_data/reset-base-data.sql", "classpath:/test_data/load-base-data.sql"])
class LegacySentenceCalculationSyncIntTest(private val mockPrisonService: MockPrisonService) : IntegrationTestBase() {
  @Autowired
  lateinit var legacySentenceCalculationRepository: LegacySentenceCalculationRepository

  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Autowired
  lateinit var approvedDatesSubmissionRepository: ApprovedDatesSubmissionRepository

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
  fun `Can create a NOMIS only calc via sync`() {
    val aNomisCalc = aMinimalLegacySentenceCalculationDto().copy(prisonerId = PRISONER_ID)

    val response = syncANewLegacyCalc(aNomisCalc)

    assertThat(response).isEqualTo(LegacySentenceCalculationToCrdsMappingResult(aNomisCalc.nomisCalculationId, null))

    val legacySentenceCalculation = legacySentenceCalculationRepository.getByPrisonerId(PRISONER_ID)
    assertThat(legacySentenceCalculation).hasSize(1)
  }

  @Test
  fun `Can create a CRDS calc via sync`() {
    val prelim = createPreliminaryCalculation(PRISONER_ID)
    val confirmed = createConfirmCalculationForPrisoner(prelim.calculationRequestId, listOf(ManuallyEnteredDate(ReleaseDateType.ROTL, SubmittedDate(21, 6, 2027))))
    val aCrdsCalc = aMinimalLegacySentenceCalculationDto().copy(prisonerId = PRISONER_ID, commentText = "CRDS comment: ${confirmed.calculationReference}")

    val response = syncANewLegacyCalc(aCrdsCalc)

    assertThat(response).isEqualTo(LegacySentenceCalculationToCrdsMappingResult(aCrdsCalc.nomisCalculationId, confirmed.calculationRequestId))

    val legacySentenceCalculation = legacySentenceCalculationRepository.getByPrisonerId(PRISONER_ID)
    assertThat(legacySentenceCalculation).hasSize(1)
    assertThat(legacySentenceCalculation.first().calculationRequestId).isEqualTo(confirmed.calculationRequestId)
    assertThat(legacySentenceCalculation.first().crdsCalculationReference).isEqualTo(confirmed.calculationReference)
  }

  @Test
  fun `Can update a NOMIS only calc via sync`() {
    val aNomisCalc = aMinimalLegacySentenceCalculationDto().copy(prisonerId = PRISONER_ID, commentText = "Original comment")

    val response = syncANewLegacyCalc(aNomisCalc)
    assertThat(response).isEqualTo(LegacySentenceCalculationToCrdsMappingResult(aNomisCalc.nomisCalculationId, null))

    val updatedResponse = syncAnUpdatedLegacyCalc(aNomisCalc.copy(commentText = "Updated comment"))
    assertThat(updatedResponse).isEqualTo(LegacySentenceCalculationToCrdsMappingResult(aNomisCalc.nomisCalculationId, null))

    val legacySentenceCalculation = legacySentenceCalculationRepository.getByPrisonerId(PRISONER_ID)
    assertThat(legacySentenceCalculation).hasSize(1)
    assertThat(legacySentenceCalculation.first().commentText).isEqualTo("Updated comment")
  }

  @Test
  fun `Can update a CRDS calc via sync`() {
    val prelim = createPreliminaryCalculation(PRISONER_ID)
    val confirmed = createConfirmCalculationForPrisoner(prelim.calculationRequestId, listOf(ManuallyEnteredDate(ReleaseDateType.ROTL, SubmittedDate(21, 6, 2027))))
    val aCrdsCalc = aMinimalLegacySentenceCalculationDto().copy(prisonerId = PRISONER_ID, commentText = "CRDS comment: ${confirmed.calculationReference}")

    val response = syncANewLegacyCalc(aCrdsCalc)
    assertThat(response).isEqualTo(LegacySentenceCalculationToCrdsMappingResult(aCrdsCalc.nomisCalculationId, confirmed.calculationRequestId))

    val secondPrelim = createPreliminaryCalculation(PRISONER_ID)
    val secondConfirmed = createConfirmCalculationForPrisoner(secondPrelim.calculationRequestId, listOf(ManuallyEnteredDate(ReleaseDateType.ROTL, SubmittedDate(21, 6, 2027))))
    val updatedResponse = syncAnUpdatedLegacyCalc(aCrdsCalc.copy(commentText = "CRDS comment: ${secondConfirmed.calculationReference}"))
    assertThat(updatedResponse).isEqualTo(LegacySentenceCalculationToCrdsMappingResult(aCrdsCalc.nomisCalculationId, secondConfirmed.calculationRequestId))

    val legacySentenceCalculation = legacySentenceCalculationRepository.getByPrisonerId(PRISONER_ID)
    assertThat(legacySentenceCalculation).hasSize(1)
    assertThat(legacySentenceCalculation.first().calculationRequestId).isEqualTo(secondConfirmed.calculationRequestId)
    assertThat(legacySentenceCalculation.first().crdsCalculationReference).isEqualTo(secondConfirmed.calculationReference)
  }

  private fun syncANewLegacyCalc(request: LegacySentenceCalculationDto) = webTestClient.post()
    .uri("/legacy-data/sync")
    .accept(APPLICATION_JSON)
    .contentType(APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .bodyValue(request)
    .exchange()
    .expectStatus().isOk
    .expectBody<LegacySentenceCalculationToCrdsMappingResult>()
    .returnResult().responseBody!!

  private fun syncAnUpdatedLegacyCalc(request: LegacySentenceCalculationDto) = webTestClient.put()
    .uri("/legacy-data/sync")
    .accept(APPLICATION_JSON)
    .contentType(APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .bodyValue(request)
    .exchange()
    .expectStatus().isOk
    .expectBody<LegacySentenceCalculationToCrdsMappingResult>()
    .returnResult().responseBody!!

  companion object {
    private const val PRISONER_ID = "default"
  }
}
