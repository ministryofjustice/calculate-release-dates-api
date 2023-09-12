package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideResponse

class SpecialistSupportIntTest : IntegrationTestBase() {

  @Test
  fun `Store a genuine override record`() {
    val preliminaryCalculation = createPreliminaryCalculation(CalculationIntTest.PRISONER_ID)
    val responseBody = webTestClient.post()
      .uri("/specialist-support/genuine-override")
      .accept(MediaType.APPLICATION_JSON)
      .bodyValue(
        GenuineOverrideRequest(
          "a reason",
          preliminaryCalculation.calculationReference.toString(),
          null,
          false,
        ),
      )
      .headers(setAuthorisation(roles = listOf("ROLE_CRDS_SPECIALIST_SUPPORT")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(GenuineOverrideResponse::class.java)
      .returnResult().responseBody
    assertThat(responseBody).isNotNull
    assertThat(responseBody!!.originalCalculationRequest).isEqualTo(preliminaryCalculation.calculationReference.toString())
  }

  private fun createPreliminaryCalculation(prisonerid: String): CalculatedReleaseDates = webTestClient.post()
    .uri("/calculation/$prisonerid")
    .accept(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(CalculatedReleaseDates::class.java)
    .returnResult().responseBody

  companion object {
    const val PRISONER_ID = "default"
    const val PRISONER_ERROR_ID = "123CBA"
    val BOOKING_ID = PRISONER_ID.hashCode().toLong()
    val BOOKING_ERROR_ID = PRISONER_ERROR_ID.hashCode().toLong()
  }
}
