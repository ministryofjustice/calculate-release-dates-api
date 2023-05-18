package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase

class ManualCalculationIntTest : IntegrationTestBase() {

  @Test
  fun `Check if booking has indeterminate sentences`() {
    val hasIndeterminateSentences = webTestClient.get()
      .uri("/manual-calculation/$BOOKING_ID/has-indeterminate-sentences")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(Boolean::class.java)
      .returnResult().responseBody!!

    assertThat(hasIndeterminateSentences).isFalse()
  }

  companion object {
    private const val PRISONER_ID = "default"
    val BOOKING_ID = PRISONER_ID.hashCode().toLong()
  }
}
