package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NonFridayReleaseDay
import java.time.LocalDate

class NonFridayReleaseDayIntTest : IntegrationTestBase() {

  @Test
  fun `NonFridayReleaseDayIntTest test weekend adjustment`() {
    val result =
      webTestClient.get()
        .uri("/non-friday-release/$SATURDAY")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody(NonFridayReleaseDay::class.java)
        .returnResult().responseBody!!

    assertEquals(THURSDAY, result.date)
    assertTrue(result.usePolicy)
  }

  @Test
  fun `past date results in bad request`() {
    webTestClient.get()
      .uri("/non-friday-release/$PAST_DATE")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isBadRequest

  }

  companion object {
    val SATURDAY: LocalDate = LocalDate.of(2023, 10, 7)
    val THURSDAY: LocalDate = LocalDate.of(2023, 10, 5)
    val PAST_DATE: LocalDate = LocalDate.MIN
  }
}
