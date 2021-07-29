package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.TestData

class TestDataIntTest : IntegrationTestBase() {

  @Test
  fun `Get a list of test data items`() {
    val result = webTestClient.get()
      .uri("/test/data")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_CALCULATE_SENTENCE_ADMIN")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBodyList(TestData::class.java)
      .returnResult().responseBody

    log.info("Expect OK: Result returned $result")
    assertThat(result?.size).isEqualTo(3)
    assertThat(result).extracting("key").containsAll(listOf("A", "B", "C"))
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
}
