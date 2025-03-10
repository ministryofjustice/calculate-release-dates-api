package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.PcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository

class ComparisonEventIntTest(private val mockManageOffencesClient: MockManageOffencesClient, private val mockPrisonService: MockPrisonService) : SqsIntegrationTestBase() {

  @Autowired
  lateinit var comparisonPersonRepository: ComparisonPersonRepository

  @Autowired
  lateinit var comparisonRepository: ComparisonRepository

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

  private fun getComparison(comparisonRef: String): ComparisonOverview = webTestClient.get()
    .uri("/comparison/{comparisonId}", comparisonRef)
    .accept(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(ComparisonOverview::class.java)
    .returnResult().responseBody!!

  private fun createComparison(prisonId: String, comparisonType: ComparisonType = ComparisonType.ESTABLISHMENT_FULL): Comparison {
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
      it!!.comparisonStatus.name == ComparisonStatusValue.COMPLETED.name
    }
    return result
  }
}
