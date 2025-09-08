package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualCalculationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntryRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntrySelectedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmittedDate
import java.time.LocalDate

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
      .bodyValue(ManualEntryRequest(listOf(ManualEntrySelectedDate(ReleaseDateType.None, "None", null)), 1L, ""))
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ManualCalculationResponse::class.java)
      .returnResult().responseBody!!
    assertThat(response.calculationRequestId).isNotNull
  }

  @Test
  fun `Stores successful manual calculation triggered by DTO having SEC104 sentence terms`() {
    val response = webTestClient.post()
      .uri("/manual-calculation/CRS-2333-1")
      .bodyValue(
        ManualEntryRequest(
          listOf(
            ManualEntrySelectedDate(
              ReleaseDateType.CRD,
              "CRD",
              SubmittedDate(1, 1, LocalDate.now().year + 1),
            ),
          ),
          1L,
          "",
        ),
      )
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ManualCalculationResponse::class.java)
      .returnResult().responseBody!!
    assertThat(response.calculationRequestId).isNotNull
  }

  @Test
  fun `Stores successful manual calculation triggered by having invalid SHPO offence`() {
    val response = webTestClient.post()
      .uri("/manual-calculation/CRS-2333-2")
      .bodyValue(
        ManualEntryRequest(
          listOf(
            ManualEntrySelectedDate(
              ReleaseDateType.CRD,
              "CRD",
              SubmittedDate(1, 1, LocalDate.now().year + 1),
            ),
          ),
          1L,
          "",
        ),
      )
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ManualCalculationResponse::class.java)
      .returnResult().responseBody!!
    assertThat(response.calculationRequestId).isNotNull
  }

  @Test
  fun `Confirm previous manual calculation does not exist for SHPO validation fail`() {
    val response = webTestClient.get()
      .uri("/manual-calculation/CRS-2437/has-existing-calculation")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(Boolean::class.java)
      .returnResult().responseBody!!
    assertThat(response).isEqualTo(false)
  }

  @Test
  fun `Confirm previous manual calculation exists for SHPO validation fail`() {
    val newCalculation = webTestClient.post()
      .uri("/manual-calculation/CRS-2437")
      .bodyValue(
        ManualEntryRequest(
          reasonForCalculationId = 1,
          otherReasonDescription = "",
          selectedManualEntryDates = listOf(
            ManualEntrySelectedDate(
              ReleaseDateType.PED,
              "PED",
              SubmittedDate(
                day = 1,
                month = 1,
                year = 2040,
              ),
            ),
          ),
        ),
      )
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ManualCalculationResponse::class.java)
      .returnResult().responseBody!!
    assertThat(newCalculation.calculationRequestId).isNotNull

    val previousCalculationCheck = webTestClient.get()
      .uri("/manual-calculation/CRS-2437/has-existing-calculation")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(Boolean::class.java)
      .returnResult().responseBody!!
    assertThat(previousCalculationCheck).isEqualTo(true)
  }

  companion object {
    private const val PRISONER_ID = "default"
    val BOOKING_ID = PRISONER_ID.hashCode().toLong()
  }
}
