package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.calculation

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.CalculationIntTest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.PrisonApiExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation

class CalculationExamplesIntTest: IntegrationTestBase() {
  private val jsonTransformation = JsonTransformation()
  private val objectMapper = TestUtil.objectMapper()

  @ParameterizedTest
  @CsvFileSource(resources = ["/test_data/api_integration/calculation-integration-examples.csv"], numLinesToSkip = 1)
  fun runCalculationExamples(prisonerJson: String, exampleId: String, error: String?) {
    val bookingId =  exampleId.hashCode().toLong()
    var prisoner = jsonTransformation.loadPrisonerDetails(prisonerJson)
    prisoner = prisoner.copy(bookingId = bookingId)
    PrisonApiExtension.prisonApi.stubGetPrisonerDetails(exampleId, objectMapper.writeValueAsString(prisoner))

    val offenceJson = jsonTransformation.getSentenceAndOffencesJson(exampleId)
    PrisonApiExtension.prisonApi.stubGetSentencesAndOffences(bookingId, offenceJson)

    val adjustmentJson = jsonTransformation.getAdjustmentsJson(exampleId)
    PrisonApiExtension.prisonApi.stubGetSentenceAdjustments(bookingId, adjustmentJson)


    val response = webTestClient.post()
      .uri("/calculation/$exampleId")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(BookingCalculation::class.java)
      .returnResult().responseBody


    log.error(response.toString())


  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}