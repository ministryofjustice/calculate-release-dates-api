package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserQuestions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
@ActiveProfiles("afterpcsc")
class CalculationUserInputAfterPcscIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var objectMapper: ObjectMapper

  @Test
  fun `Service will return which sentences may fall under SDS+ and need user input`() {
    val response = webTestClient.get()
      .uri("/calculation/USERINPUT/user-questions")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculationUserQuestions::class.java)
      .returnResult().responseBody

    // Halfway
    assertThat(response.sentenceQuestions.size).isEqualTo(1)
    assertThat(response.sentenceQuestions[0].userInputType).isEqualTo(UserInputType.ORIGINAL)
  }
}
