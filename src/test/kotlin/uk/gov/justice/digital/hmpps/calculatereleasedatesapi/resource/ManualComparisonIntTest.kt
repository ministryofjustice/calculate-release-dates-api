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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
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
    val result = createManualComparison("EDS")

    assertEquals(ComparisonType.MANUAL, result.comparisonType)
    assertEquals(0, result.numberOfPeopleCompared)
    val comparison = comparisonRepository.findByComparisonShortReference(result.comparisonShortReference)
    assertEquals(1, comparison!!.numberOfPeopleCompared)
    val personComparison = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id)[0]
    assertTrue(personComparison.isValid)
    assertFalse(personComparison.isMatch)
    assertEquals("EDS", personComparison.person)
  }

  @Test
  fun `Retrieve comparison person must return all dates`() {
    val comparison = createManualComparison("EDS")
    val storedComparison = comparisonRepository.findByComparisonShortReference(comparison.comparisonShortReference)
    val comparisonPerson = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(storedComparison!!.id)[0]
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

  private fun createManualComparison(prisonerId: String): Comparison {
    val request = ManualComparisonInput(listOf(prisonerId))
    val result = webTestClient.post()
      .uri("/comparison/manual")
      .accept(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_MANUAL_COMPARER")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(Comparison::class.java)
      .returnResult().responseBody!!
    await untilCallTo { comparisonRepository.findByComparisonShortReference(result.comparisonShortReference) } matches {
      it!!.comparisonStatus.name == ComparisonStatusValue.COMPLETED.name
    }
    return result
  }
}
