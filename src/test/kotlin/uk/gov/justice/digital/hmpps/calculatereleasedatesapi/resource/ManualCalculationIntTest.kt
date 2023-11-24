package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualCalculationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntryRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntrySelectedDate

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

  @Test
  fun `Storing a no dates manual entry is successful`() {
    val response = webTestClient.post()
      .uri("/manual-calculation/$PRISONER_ID")
      .bodyValue(ManualEntryRequest(listOf(ManualEntrySelectedDate(ReleaseDateType.None, "None", null))))
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ManualCalculationResponse::class.java)
      .returnResult().responseBody!!
    assertThat(response.calculationRequestId).isNotNull
  }

  companion object {
    private const val PRISONER_ID = "default"
    val BOOKING_ID = PRISONER_ID.hashCode().toLong()
  }
}
