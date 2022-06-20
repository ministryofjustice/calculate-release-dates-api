package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSentenceUserInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserQuestions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
@ActiveProfiles("beforepcsc")
class CalculationUserInputBeforeIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Autowired
  lateinit var objectMapper: ObjectMapper

  @Test
  @Transactional(readOnly = true)
  fun `Use a user input that differs from NOMIS and check its persisted through prelim, confirmed and view`() {
    val userInput = CalculationUserInputs(
      listOf(
        CalculationSentenceUserInput(
          sentenceSequence = 1,
          offenceCode = "SX03014",
          isScheduleFifteenMaximumLife = false // Different to NOMIS.
        )
      )
    )
    val prelimResponse = webTestClient.post()
      .uri("/calculation/USERINPUT")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(userInput))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody

    // Halfway
    assertThat(prelimResponse.dates[CRD]).isEqualTo(LocalDate.of(2028, 1, 10))

    val confirmResponse = webTestClient.post()
      .uri("/calculation/USERINPUT/confirm/${prelimResponse.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(CalculationFragments("<p>BREAKDOWN</p>")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(confirmResponse.dates[CRD]).isEqualTo(LocalDate.of(2028, 1, 10))

    val userInputResponse = webTestClient.get()
      .uri("/calculation/calculation-user-input/${confirmResponse.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculationUserInputs::class.java)
      .returnResult().responseBody!!

    assertThat(userInputResponse).isEqualTo(userInput)

    val dbRequest = calculationRequestRepository.findById(confirmResponse.calculationRequestId).get()
    assertThat(dbRequest.calculationRequestUserInputs).isNotEmpty
    assertThat(dbRequest.calculationRequestUserInputs[0].nomisMatches).isFalse
    assertThat(dbRequest.calculationRequestUserInputs[0].userChoice).isFalse
  }

  @Test
  @Transactional(readOnly = true)
  fun `Use a user input that is the same as NOMIS`() {
    val userInput = CalculationUserInputs(
      listOf(
        CalculationSentenceUserInput(
          sentenceSequence = 1,
          offenceCode = "SX03014",
          isScheduleFifteenMaximumLife = true // same as NOMIS.
        )
      )
    )
    val prelimResponse = webTestClient.post()
      .uri("/calculation/USERINPUT")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(userInput))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody

    // Halfway
    assertThat(prelimResponse.dates[CRD]).isEqualTo(LocalDate.of(2030, 1, 9))

    val dbRequest = calculationRequestRepository.findById(prelimResponse.calculationRequestId).get()
    assertThat(dbRequest.calculationRequestUserInputs).isNotEmpty
    assertThat(dbRequest.calculationRequestUserInputs[0].nomisMatches).isTrue
    assertThat(dbRequest.calculationRequestUserInputs[0].userChoice).isTrue
  }

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
    assertThat(response.sentenceQuestions[0].userInputType).isEqualTo(UserInputType.SCHEDULE_15_ATTRACTING_LIFE)
  }
}
