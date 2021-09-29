package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import javax.persistence.EntityNotFoundException

class CalculationIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Test
  fun `Run calculation for a prisoner (based on example 13 from the unit tests) + test input JSON in DB`() {
    val result = webTestClient.get()
      .uri("/calculation/by-prisoner-id/A1234AA")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_CRD_ADMIN")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(BookingCalculation::class.java)
      .returnResult().responseBody

    val calculationRequest = calculationRequestRepository.findById(result.calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists for id ${result.calculationRequestId}") }

    assertThat(result).isNotNull
    assertThat(result.dates[SLED]).isEqualTo(LocalDate.of(2016, 11, 6))
    assertThat(result.dates[CRD]).isEqualTo(LocalDate.of(2016, 1, 6))
    assertThat(result.dates[TUSED]).isEqualTo(LocalDate.of(2017, 1, 6))
    assertThat(calculationRequest.inputData["offender"]["reference"].asText()).isEqualTo("A1234AA")
    assertThat(calculationRequest.inputData["sentences"][0]["offence"]["committedAt"].asText()).isEqualTo("2015-03-17")
  }
}
