package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.databind.ObjectMapper
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonPersonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository

class ComparisonIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var comparisonPersonRepository: ComparisonPersonRepository

  @Autowired
  lateinit var comparisonRepository: ComparisonRepository

  @Autowired
  lateinit var objectMapper: ObjectMapper

  @BeforeEach
  fun clearTables() {
    comparisonPersonRepository.deleteAll()
    comparisonRepository.deleteAll()
  }

  @Test
  fun `Run comparison on a prison must compare all viable prisoners`() {
    val result = createComparison("ABC")

    assertEquals(false, result.manualInput)
    assertEquals(0, result.numberOfPeopleCompared)
    val comparison = comparisonRepository.findByManualInputAndComparisonShortReference(false, result.comparisonShortReference)
    assertEquals(1, comparison!!.numberOfPeopleCompared)
    val personComparison = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(comparison.id)[0]
    assertTrue(personComparison.isValid)
    assertFalse(personComparison.isMatch)
    assertEquals("Z0020ZZ", personComparison.person)
  }

  @Test
  fun `Retrieve comparisons must return summary`() {
    val comparison = createComparison("ABC")
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
    assertEquals(1, result[0].numberOfPeopleCompared)
    assertEquals(1, result[0].numberOfMismatches)
  }

  @Test
  fun `Retrieve comparison must return mismatches associated`() {
    val comparison = createComparison("ABC")
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
    assertEquals(1, result.numberOfPeopleCompared)
    assertEquals(1, result.numberOfMismatches)
    assertTrue(result.mismatches[0].isValid)
    assertFalse(result.mismatches[0].isMatch)
    assertEquals("Z0020ZZ", result.mismatches[0].personId)
  }

  @Test
  fun `Retrieve comparison person must return all dates`() {
    val comparison = createComparison("ABC")
    val storedComparison = comparisonRepository.findByManualInputAndComparisonShortReference(false, comparison.comparisonShortReference)
    val comparisonPerson = comparisonPersonRepository.findByComparisonIdIsAndIsMatchFalse(storedComparison!!.id)[0]
    println("--- Sentences = ${comparisonPerson.sdsPlusSentencesIdentified.size()} -----")
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

  private fun createComparison(prisonId: String): Comparison {
    val request = ComparisonInput(objectMapper.createObjectNode(), prisonId)
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
    await untilCallTo { comparisonRepository.findByManualInputAndComparisonShortReference(false, result.comparisonShortReference) } matches {
      it!!.comparisonStatus.name == ComparisonStatusValue.COMPLETED.name
    }
    return result
  }
}
