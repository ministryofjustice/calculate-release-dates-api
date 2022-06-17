package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
@ActiveProfiles("beforepcsc")
class CalculationBeforePcscIntTest : IntegrationTestBase() {

  @Test
  fun `Run validation on unsupported prisoner data`() {
    webTestClient.post()
      .uri("/calculation/$UNSUPPORTED_PRISONER_PRISONER_ID/validate")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isNoContent
  }

  companion object {
    const val UNSUPPORTED_PRISONER_PRISONER_ID = "UNSUPP_PRIS"
  }
}
