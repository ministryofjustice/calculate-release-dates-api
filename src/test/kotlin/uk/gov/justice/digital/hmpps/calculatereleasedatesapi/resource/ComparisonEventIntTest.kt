package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyCategory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyImpact
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancyPriority
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.DiscrepancySubCategory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonDiscrepancySummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonPersonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CreateComparisonDiscrepancyRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DiscrepancyCause
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.PcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService

class ComparisonEventIntTest(private val mockManageOffencesClient: MockManageOffencesClient, private val mockPrisonService: MockPrisonService) : SqsIntegrationTestBase() {

  @Autowired
  lateinit var comparisonPersonRepository: ComparisonPersonRepository

  @Autowired
  lateinit var comparisonRepository: ComparisonRepository

  @MockitoSpyBean
  lateinit var validationService: ValidationService

  @MockitoSpyBean
  lateinit var prisonService: PrisonService

  @BeforeEach
  fun clearTables() {
    comparisonPersonRepository.deleteAll()
    comparisonRepository.deleteAll()
    mockManageOffencesClient.withPCSCMarkersResponse(
      OffencePcscMarkers(
        offenceCode = "CD79009",
        pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = false, inListD = false),
      ),
      OffencePcscMarkers(
        offenceCode = "TR68132",
        pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = false, inListD = false),
      ),
      offences = "CD79009,TR68132",
    )
    mockPrisonService.withInstAgencies(
      listOf(
        Agency("PRIS", "prison PRIS"),
      ),
    )
  }

  @Test
  fun `Run comparison on a prison must compare all viable prisoners event process`() {
    val result = createComparison("PRIS")

    assertEquals(ComparisonType.ESTABLISHMENT_FULL, result.comparisonType)
    assertEquals(0, result.numberOfPeopleCompared)
    val completedComparison = getComparison(result.comparisonShortReference)
    assertEquals(4, completedComparison.numberOfPeopleCompared)
    val comparison = comparisonRepository.findByComparisonShortReference(result.comparisonShortReference)
    val personComparison = comparisonPersonRepository.findByComparisonId(comparison!!.id).find { it.person == "default" }!!
    assertTrue(personComparison.isValid)
    assertTrue(personComparison.isMatch)
    assertEquals("default", personComparison.person)
  }

  @Test
  fun `Run comparison on a prison must compare where there are fatal errors`() {
    doThrow(IllegalArgumentException("An exception"))
      .whenever(validationService).validateBeforeCalculation(any(), any(), any())

    val result = createComparison("PRIS")

    assertEquals(ComparisonType.ESTABLISHMENT_FULL, result.comparisonType)
    assertEquals(0, result.numberOfPeopleCompared)
    val completedComparison = getComparison(result.comparisonShortReference)
    assertEquals(4, completedComparison.numberOfPeopleCompared)
    val comparison = comparisonRepository.findByComparisonShortReference(result.comparisonShortReference)
    val personComparison = comparisonPersonRepository.findByComparisonId(comparison!!.id).find { it.person == "default" }!!
    assertFalse(personComparison.isValid)
    assertFalse(personComparison.isMatch)
    assertTrue(personComparison.isFatal)
    assertEquals("default", personComparison.person)
  }

  @Test
  fun `Retrieve comparisons must return summary`() {
    val comparison = createComparison("PRIS")
    val result = webTestClient.get()
      .uri("/comparison")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(object : ParameterizedTypeReference<List<ComparisonSummary>>() {})
      .returnResult().responseBody!!

    assertEquals(1, result.size)
    assertEquals(comparison.prison, result[0].prison)
    assertEquals(4, result[0].numberOfPeopleCompared)
    assertEquals(3, result[0].numberOfMismatches)
  }

  @Test
  fun `Retrieve comparison must return mismatches associated`() {
    val comparison = createComparison("PRIS")
    val result = webTestClient.get()
      .uri("/comparison/{comparisonId}", comparison.comparisonShortReference)
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ComparisonOverview::class.java)
      .returnResult().responseBody!!

    assertEquals(comparison.prison, result.prison)
    assertEquals(4, result.numberOfPeopleCompared)
    assertEquals(3, result.numberOfMismatches)
    val edsMisMatch = result.mismatches.find { it.personId == "EDS" }!!
    assertTrue(edsMisMatch.isValid)
    assertFalse(edsMisMatch.isMatch)
    assertEquals("EDS", edsMisMatch.personId)
    assertEquals("prison PRIS", edsMisMatch.establishment)
  }

  @Test
  fun `Retrieve comparison person must return all dates`() {
    val comparison = createComparison("PRIS")
    val storedComparison = comparisonRepository.findByComparisonShortReference(comparison.comparisonShortReference)
    val comparisonPerson = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(storedComparison!!.id).find { it.person == "EDS" }!!
    val result = webTestClient.get()
      .uri("/comparison/{comparisonId}/mismatch/{mismatchId}", comparison.comparisonShortReference, comparisonPerson.shortReference)
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ComparisonPersonOverview::class.java)
      .returnResult().responseBody!!
    assertTrue(result.isValid)
    assertFalse(result.isMatch)
    assertEquals(comparisonPerson.person, result.personId)
  }

  @Test
  fun `Retrieve comparison person discrepancy should return discrepancy summary`() {
    val comparison = createComparison("PRIS")
    val storedComparison = comparisonRepository.findByComparisonShortReference(comparison.comparisonShortReference)
    val comparisonPerson = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(storedComparison!!.id)[0]

    val createdDiscrepancy = createComparisonPersonDiscrepancy(comparison, comparisonPerson)

    val result = webTestClient.get()
      .uri("/comparison/{comparisonReference}/mismatch/{comparisonPersonReference}/discrepancy", comparison.comparisonShortReference, comparisonPerson.shortReference)
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ComparisonDiscrepancySummary::class.java)
      .returnResult().responseBody!!

    assertEquals(createdDiscrepancy, result)
  }

  @Test
  fun `Run comparison that fatally errors in setup`() {
    doThrow(IllegalArgumentException("An exception"))
      .whenever(prisonService).getCalculablePrisonerByPrison(eq("PRIS"))

    val result = createComparison("PRIS", completeStatus = ComparisonStatusValue.ERROR)

    assertEquals(ComparisonType.ESTABLISHMENT_FULL, result.comparisonType)
    assertEquals(0, result.numberOfPeopleCompared)
    assertEquals(ComparisonStatusValue.SETUP.name, result.comparisonStatus.name)

    val errorComparison = getComparison(result.comparisonShortReference)
    assertEquals(ComparisonStatusValue.ERROR.name, errorComparison.comparisonStatus.name)
  }

  private fun getComparison(comparisonRef: String): ComparisonOverview = webTestClient.get()
    .uri("/comparison/{comparisonId}", comparisonRef)
    .accept(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(ComparisonOverview::class.java)
    .returnResult().responseBody!!

  private fun createComparison(prisonId: String, comparisonType: ComparisonType = ComparisonType.ESTABLISHMENT_FULL, completeStatus: ComparisonStatusValue = ComparisonStatusValue.COMPLETED): Comparison {
    val request = ComparisonInput(objectMapper.createObjectNode(), prisonId, comparisonType)
    val result = webTestClient.post()
      .uri("/comparison")
      .accept(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(Comparison::class.java)
      .returnResult().responseBody!!
    await untilCallTo { getComparison(result.comparisonShortReference) } matches {
      it!!.comparisonStatus.name == completeStatus.name
    }
    return result
  }

  private fun createComparisonPersonDiscrepancy(comparison: Comparison, comparisonPerson: ComparisonPerson): ComparisonDiscrepancySummary {
    val discrepancyCauses = listOf(DiscrepancyCause(DiscrepancyCategory.PED, DiscrepancySubCategory.DATE_NOT_CALCULATED))
    val request = CreateComparisonDiscrepancyRequest(DiscrepancyImpact.POTENTIAL_UNLAWFUL_DETENTION, discrepancyCauses, "detail", DiscrepancyPriority.HIGH_RISK, "action")
    val result = webTestClient.post()
      .uri("/comparison/{comparisonReference}/mismatch/{mismatchReference}/discrepancy", comparison.comparisonShortReference, comparisonPerson.shortReference)
      .accept(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ComparisonDiscrepancySummary::class.java)
      .returnResult().responseBody!!
    return result
  }
}
