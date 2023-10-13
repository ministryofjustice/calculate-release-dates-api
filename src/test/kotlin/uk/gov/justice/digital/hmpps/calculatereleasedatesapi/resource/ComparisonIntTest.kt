package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput

class ComparisonIntTest : IntegrationTestBase() {

  @Test
  fun `Run comparison on a prison must compare all viable prisoners`() {
    val request = ComparisonInput(null, false, "ABC")
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

    assertEquals(request.manualInput, result.manualInput)
  }
}
