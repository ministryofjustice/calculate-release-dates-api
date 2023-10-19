package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
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

  @Test
  fun `Run comparison on a prison must compare all viable prisoners`() {
    val request = ComparisonInput(objectMapper.createObjectNode(), "ABC")
    val result =
      webTestClient.post()
        .uri("/comparison")
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATE_COMPARER")))
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody(Comparison::class.java)
        .returnResult().responseBody!!

    assertEquals(false, result.manualInput)
    assertEquals(1, result.numberOfPeopleCompared!!)
    val comparison = comparisonRepository.findByManualInputAndComparisonShortReference(false, result.comparisonShortReference)
    val personComparison = comparisonPersonRepository.findByComparisonIdIs(comparison!!.id)[0]
    assertTrue(personComparison.isValid)
    assertFalse(personComparison.isMatch)
    assertEquals("Z0020ZZ", personComparison.person)
  }
}
