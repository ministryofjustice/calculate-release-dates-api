package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessages
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
@ActiveProfiles("afterpcsc")
class CalculationAfterPcscIntTest : IntegrationTestBase() {

  @Test
  fun `Run validation on unsupported prisoner data`() {
    val validationMessages: ValidationMessages = webTestClient.post()
      .uri("/calculation/$UNSUPPORTED_PRISONER_PRISONER_ID/validate")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ValidationMessages::class.java)
      .returnResult().responseBody!!

    assertThat(validationMessages.type).isEqualTo(ValidationType.UNSUPPORTED)
    assertThat(validationMessages.messages).hasSize(1)
    assertThat(validationMessages.messages[0]).matches { it.code == ValidationCode.PRISONER_SUBJECT_TO_PTD }
  }

  companion object {
    const val UNSUPPORTED_PRISONER_PRISONER_ID = "UNSUPP_PRIS"
  }
}
