package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonPersonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository

class ManualComparisonIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var comparisonPersonRepository: ComparisonPersonRepository

  @Autowired
  lateinit var comparisonRepository: ComparisonRepository

  @Test
  fun `Run comparison on a prison must compare all viable prisoners`() {
    val result = createManualComparison("Z0020ZZ")

    assertEquals(true, result.manualInput)
    assertEquals(0, result.numberOfPeopleCompared)
    val comparison = comparisonRepository.findByManualInputAndComparisonShortReference(true, result.comparisonShortReference)
    assertEquals(1, comparison!!.numberOfPeopleCompared)
    val personComparison = comparisonPersonRepository.findByComparisonIdIs(comparison.id)[0]
    assertTrue(personComparison.isValid)
    assertFalse(personComparison.isMatch)
    assertEquals("Z0020ZZ", personComparison.person)
  }

  @Test
  fun `Retrieve comparison person must return all dates`() {
    val comparison = createManualComparison("Z0020ZZ")
    val storedComparison = comparisonRepository.findByManualInputAndComparisonShortReference(true, comparison.comparisonShortReference)
    val comparisonPerson = comparisonPersonRepository.findByComparisonIdIs(storedComparison!!.id)[0]
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
    await untilCallTo { comparisonRepository.findByManualInputAndComparisonShortReference(true, result.comparisonShortReference) } matches {
      it!!.comparisonStatus.name == ComparisonStatusValue.COMPLETED.name
    }
    return result
  }
}
