package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationRequestModel
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSentenceUserInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmitCalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class CalculationUserInputIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Test
  @Transactional
  fun `User input is kept for view journey even though calculation questions have been deprecated`() {
    val userInput = CalculationUserInputs(
      calculateErsed = true,
      useOffenceIndicators = false,
      sentenceCalculationUserInputs = listOf(
        CalculationSentenceUserInput(
          sentenceSequence = 1,
          offenceCode = "SX03014",
          userInputType = UserInputType.ORIGINAL,
          // difference to NOMIS.
          userChoice = false,
        ),
      ),
    )
    val prelimResponse = webTestClient.post()
      .uri("/calculation/USERINPUT")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(CalculationRequestModel(userInput, 1L)))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    // Release at 2/3 which is the same as NOMIS as user specified sentences are no longer applicable
    assertThat(prelimResponse.dates[ReleaseDateType.CRD]).isEqualTo(LocalDate.of(2030, 1, 9))

    val confirmResponse = webTestClient.post()
      .uri("/calculation/confirm/${prelimResponse.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(SubmitCalculationRequest(CalculationFragments("<p>BREAKDOWN</p>"), emptyList())))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(prelimResponse.dates[ReleaseDateType.CRD]).isEqualTo(LocalDate.of(2030, 1, 9))

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
    assertThat(dbRequest.calculationRequestUserInput).isNotNull
    assertThat(dbRequest.calculationRequestUserInput!!.calculateErsed).isTrue
    assertThat(dbRequest.calculationRequestUserInput!!.useOffenceIndicators).isFalse
    assertThat(dbRequest.calculationRequestUserInput!!.calculationRequestSentenceUserInputs).isNotEmpty
    assertThat(dbRequest.calculationRequestUserInput!!.calculationRequestSentenceUserInputs[0].nomisMatches).isTrue
    assertThat(dbRequest.calculationRequestUserInput!!.calculationRequestSentenceUserInputs[0].userChoice).isFalse
  }

  @Test
  @Transactional
  fun `Use a user input that is the same as NOMIS`() {
    val userInput = CalculationUserInputs(
      calculateErsed = false,
      useOffenceIndicators = false,
      sentenceCalculationUserInputs = listOf(
        CalculationSentenceUserInput(
          sentenceSequence = 1,
          offenceCode = "SX03014",
          userInputType = UserInputType.ORIGINAL,
          // same as NOMIS.
          userChoice = true,
        ),
      ),
    )
    val prelimResponse = webTestClient.post()
      .uri("/calculation/USERINPUT")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(CalculationRequestModel(userInput, 1L)))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    // Two thirds release (Same as NOMIS)
    assertThat(prelimResponse.dates[ReleaseDateType.CRD]).isEqualTo(LocalDate.of(2030, 1, 9))

    val dbRequest = calculationRequestRepository.findById(prelimResponse.calculationRequestId).get()

    assertThat(dbRequest.calculationRequestUserInput).isNotNull
    assertThat(dbRequest.calculationRequestUserInput!!.calculateErsed).isFalse
    assertThat(dbRequest.calculationRequestUserInput!!.useOffenceIndicators).isFalse
    assertThat(dbRequest.calculationRequestUserInput!!.calculationRequestSentenceUserInputs).isNotEmpty
    assertThat(dbRequest.calculationRequestUserInput!!.calculationRequestSentenceUserInputs[0].nomisMatches).isTrue
    assertThat(dbRequest.calculationRequestUserInput!!.calculationRequestSentenceUserInputs[0].userChoice).isTrue
  }

  @Test
  @Transactional
  fun `Use NOMIS markers rather than user input`() {
    val userInput = CalculationUserInputs(
      calculateErsed = false,
      useOffenceIndicators = true,
    )
    val prelimResponse = webTestClient.post()
      .uri("/calculation/USERINPUT")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(CalculationRequestModel(userInput, 1L)))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    // Two thirds release (Same as NOMIS)
    assertThat(prelimResponse.dates[ReleaseDateType.CRD]).isEqualTo(LocalDate.of(2030, 1, 9))

    val dbRequest = calculationRequestRepository.findById(prelimResponse.calculationRequestId).get()

    assertThat(dbRequest.calculationRequestUserInput).isNotNull
    assertThat(dbRequest.calculationRequestUserInput!!.calculateErsed).isFalse
    assertThat(dbRequest.calculationRequestUserInput!!.useOffenceIndicators).isTrue
    assertThat(dbRequest.calculationRequestUserInput!!.calculationRequestSentenceUserInputs).isEmpty()
  }
}
