package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.TestData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation

class TestDataIntTest : IntegrationTestBase() {
  private val jsonTransformation = JsonTransformation()

  @Test
  fun `Get a list of test data items`() {
    val result = webTestClient.get()
      .uri("/test/data")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_CRD_ADMIN")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBodyList(TestData::class.java)
      .returnResult().responseBody

    log.info("Expect OK: Result returned $result")
    assertThat(result?.size).isEqualTo(4)
    assertThat(result).extracting("key").containsAll(listOf("A", "B", "C", "A1234AA"))
  }

  @Test
  fun `Forbidden (403) when incorrect roles are supplied`() {
    val result = webTestClient.get()
      .uri("/test/data")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_CALCULATE_SENTENCE_VERY_WRONG")))
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.FORBIDDEN.value())
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    log.info("Expect 403: Result was $result")
    assertThat(result?.userMessage).contains("Access is denied")
  }

  @Test
  fun `Unauthorized (401) when no token is supplied`() {
    webTestClient.get()
      .uri("/test/data")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED.value())
  }

  @Test
  fun `Test example 9 against direct calculation endpoint`() {
    val result = webTestClient.post()
      .uri("/test/calculation-by-booking")
      .bodyValue(jsonTransformation.getJsonTest("psi-examples/9.json", "overall_calculation"))
      .headers(setAuthorisation(roles = listOf("ROLE_CRD_ADMIN")))
      .exchange()
      .expectStatus().isOk
      .expectBody(BookingCalculation::class.java)
      .returnResult().responseBody
    if (result != null) {
      assertEquals(
        jsonTransformation.loadBookingCalculation("9").dates,
        result.dates
      )
    }
  }
}
