package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculationTest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationToCrdsMappingResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.LegacySentenceCalculationRepository

@Sql(scripts = ["classpath:/test_data/reset-base-data.sql", "classpath:/test_data/load-base-data.sql"])
class LegacySentenceCalculationMigrationIntTest(private val mockManageOffencesClient: MockManageOffencesClient, private val mockPrisonService: MockPrisonService) : IntegrationTestBase() {
  @Autowired
  lateinit var legacySentenceCalculationRepository: LegacySentenceCalculationRepository

  @BeforeEach
  fun setUp() {
    mockPrisonService.stubPostOffenderDates(PRISONER_ID.hashCode().toLong())
  }

  @Test
  fun `Can migrate legacy calculations for a prisoner with some matching CRDS calcs and some not`() {
    val prelim = createPreliminaryCalculation(PRISONER_ID)
    val confirmed = createConfirmCalculationForPrisoner(prelim.calculationRequestId)

    val aNomisCalc = LegacySentenceCalculationTest.aMinimalLegacySentenceCalculationDto().copy(prisonerId = PRISONER_ID)
    val aCrdsCalc = LegacySentenceCalculationTest.aFullyCompletedLegacySentenceCalculationDto().copy(prisonerId = PRISONER_ID, commentText = "CRDS: ${confirmed.calculationReference}")
    val request = LegacySentenceCalculationMigrationRequest(listOf(aNomisCalc, aCrdsCalc))

    val response = migrateAPrisoner(request)

    assertThat(response).isEqualTo(
      LegacySentenceCalculationMigrationResponse(
        listOf(
          LegacySentenceCalculationToCrdsMappingResult(aNomisCalc.nomisCalculationId, null),
          LegacySentenceCalculationToCrdsMappingResult(aCrdsCalc.nomisCalculationId, confirmed.calculationRequestId),
        ),
      ),
    )

    val legacySentenceCalculation = legacySentenceCalculationRepository.getByPrisonerId(PRISONER_ID)
    assertThat(legacySentenceCalculation).hasSize(2)
    assertThat(legacySentenceCalculation.find { it.nomisCalculationId == aNomisCalc.nomisCalculationId }).isNotNull
    assertThat(legacySentenceCalculation.find { it.nomisCalculationId == aCrdsCalc.nomisCalculationId }).isNotNull
  }

  @Test
  fun `Can remigrate legacy calculations for a prisoner which should remove any existing ones`() {
    val prelim = createPreliminaryCalculation(PRISONER_ID)
    val confirmed = createConfirmCalculationForPrisoner(prelim.calculationRequestId)

    val aNomisCalc = LegacySentenceCalculationTest.aMinimalLegacySentenceCalculationDto().copy(prisonerId = PRISONER_ID)
    val aCrdsCalc = LegacySentenceCalculationTest.aFullyCompletedLegacySentenceCalculationDto().copy(prisonerId = PRISONER_ID, commentText = "CRDS: ${confirmed.calculationReference}")

    val firstRequest = LegacySentenceCalculationMigrationRequest(listOf(aNomisCalc, aCrdsCalc))

    val firstResponse = migrateAPrisoner(firstRequest)

    assertThat(firstResponse).isEqualTo(
      LegacySentenceCalculationMigrationResponse(
        listOf(
          LegacySentenceCalculationToCrdsMappingResult(aNomisCalc.nomisCalculationId, null),
          LegacySentenceCalculationToCrdsMappingResult(aCrdsCalc.nomisCalculationId, confirmed.calculationRequestId),
        ),
      ),
    )

    val firstPassLegacySentenceCalculation = legacySentenceCalculationRepository.getByPrisonerId(PRISONER_ID)
    assertThat(firstPassLegacySentenceCalculation).hasSize(2)
    assertThat(firstPassLegacySentenceCalculation.find { it.nomisCalculationId == aNomisCalc.nomisCalculationId }).isNotNull
    assertThat(firstPassLegacySentenceCalculation.find { it.nomisCalculationId == aCrdsCalc.nomisCalculationId }).isNotNull

    val aChangedNomisCalc = LegacySentenceCalculationTest.aMinimalLegacySentenceCalculationDto().copy(nomisCalculationId = 98, prisonerId = PRISONER_ID)
    val aChangedCrdsCalc = LegacySentenceCalculationTest.aFullyCompletedLegacySentenceCalculationDto().copy(nomisCalculationId = 99, prisonerId = PRISONER_ID, commentText = "CRDS: ${confirmed.calculationReference}")
    val request = LegacySentenceCalculationMigrationRequest(listOf(aChangedNomisCalc, aChangedCrdsCalc))

    val secondResponse = migrateAPrisoner(request)

    assertThat(secondResponse).isEqualTo(
      LegacySentenceCalculationMigrationResponse(
        listOf(
          LegacySentenceCalculationToCrdsMappingResult(aChangedNomisCalc.nomisCalculationId, null),
          LegacySentenceCalculationToCrdsMappingResult(aChangedCrdsCalc.nomisCalculationId, confirmed.calculationRequestId),
        ),
      ),
    )

    val secondPassLegacySentenceCalculation = legacySentenceCalculationRepository.getByPrisonerId(PRISONER_ID)
    assertThat(secondPassLegacySentenceCalculation).hasSize(2)

    // the original items should've been deleted and new ones retained
    assertThat(secondPassLegacySentenceCalculation.find { it.nomisCalculationId == aNomisCalc.nomisCalculationId }).isNull()
    assertThat(secondPassLegacySentenceCalculation.find { it.nomisCalculationId == aCrdsCalc.nomisCalculationId }).isNull()
    assertThat(secondPassLegacySentenceCalculation.find { it.nomisCalculationId == aChangedNomisCalc.nomisCalculationId }).isNotNull
    assertThat(secondPassLegacySentenceCalculation.find { it.nomisCalculationId == aChangedCrdsCalc.nomisCalculationId }).isNotNull
  }

  private fun migrateAPrisoner(request: LegacySentenceCalculationMigrationRequest) = webTestClient.post()
    .uri("/legacy-data/migrate/$PRISONER_ID")
    .accept(APPLICATION_JSON)
    .contentType(APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .bodyValue(request)
    .exchange()
    .expectStatus().isOk
    .expectBody<LegacySentenceCalculationMigrationResponse>()
    .returnResult().responseBody!!

  companion object {
    private const val PRISONER_ID = "default"
  }
}
