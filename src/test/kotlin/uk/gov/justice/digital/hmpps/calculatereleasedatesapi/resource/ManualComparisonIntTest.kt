package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonPersonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.PcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository

@Sql(scripts = ["classpath:/test_data/reset-base-data.sql", "classpath:/test_data/load-base-data.sql"])
class ManualComparisonIntTest(private val mockManageOffencesClient: MockManageOffencesClient) : SqsIntegrationTestBase() {

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
  }

  @Test
  fun `Run comparison on a prison must compare all viable prisoners`() {
    val result = createManualComparison(listOf("EDS"))

    assertEquals(ComparisonType.MANUAL, result.comparisonType)
    assertEquals(0, result.numberOfPeopleCompared)
    val comparison = comparisonRepository.findByComparisonShortReference(result.comparisonShortReference)
    assertEquals(1, comparison!!.numberOfPeopleCompared)
    val personComparison = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id())[0]
    assertTrue(personComparison.isValid)
    assertFalse(personComparison.isMatch)
    assertEquals("EDS", personComparison.person)
  }

  @Test
  fun `Retrieve comparison person must return all dates`() {
    val comparison = createManualComparison(listOf("EDS"))
    val storedComparison = comparisonRepository.findByComparisonShortReference(comparison.comparisonShortReference)
    val comparisonPerson = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(storedComparison!!.id())[0]
    val result = webTestClient.get()
      .uri("/comparison/manual/{comparisonId}/mismatch/{mismatchId}", comparison.comparisonShortReference, comparisonPerson.shortReference)
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_MANUAL_COMPARER")))
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
  fun `Run comparison on a prisoner not existing`() {
    val result = createManualComparison(listOf("NOTEXIST"))

    assertEquals(ComparisonType.MANUAL, result.comparisonType)
    assertEquals(0, result.numberOfPeopleCompared)
    val comparison = comparisonRepository.findByComparisonShortReference(result.comparisonShortReference)
    assertEquals(1, comparison!!.numberOfPeopleCompared)
    val personComparison = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id())[0]
    assertTrue(personComparison.isFatal)
    assertFalse(personComparison.isMatch)
    assertEquals("404 Not Found from GET http://localhost:8332/api/offenders/NOTEXIST", personComparison.fatalException)
  }

  @Test
  fun `Run manual comparison with duplicates`() {
    val result = createManualComparison(listOf("EDS", "EDS", "EDS", "EDS"))

    assertEquals(ComparisonType.MANUAL, result.comparisonType)
    assertEquals(0, result.numberOfPeopleCompared)
    val comparison = comparisonRepository.findByComparisonShortReference(result.comparisonShortReference)
    assertEquals(1, comparison!!.numberOfPeopleCompared)
    val personComparison = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id())[0]
    assertTrue(personComparison.isValid)
    assertFalse(personComparison.isMatch)
    assertEquals("EDS", personComparison.person)
  }

  private fun createManualComparison(prisonerIds: List<String>): ComparisonDto {
    val request = ManualComparisonInput(prisonerIds)
    val result = webTestClient.post()
      .uri("/comparison/manual")
      .accept(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_MANUAL_COMPARER")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ComparisonDto::class.java)
      .returnResult().responseBody!!
    await untilCallTo { comparisonRepository.findByComparisonShortReference(result.comparisonShortReference) } matches {
      it!!.comparisonStatus.name == ComparisonStatus.COMPLETED.name
    }
    return result
  }
}
