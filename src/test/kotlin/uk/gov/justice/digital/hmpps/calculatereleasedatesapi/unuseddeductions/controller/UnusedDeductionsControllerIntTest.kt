package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.AdjustmentServiceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.AdjustmentServiceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UnusedDeductionCalculationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate
import java.util.UUID

class UnusedDeductionsControllerIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var objectMapper: ObjectMapper

  @Test
  fun `Run unused deductions calculation`() {
    val adjustments = listOf(
      AdjustmentServiceAdjustment(
        fromDate = LocalDate.of(2020, 2, 1),
        toDate = LocalDate.of(2021, 1, 31),
        daysTotal = 396,
        effectiveDays = 396,
        bookingId = "UNUSED".hashCode().toLong(),
        sentenceSequence = 4,
        adjustmentType = AdjustmentServiceAdjustmentType.REMAND,
        person = "UNUSED",
        id = UUID.randomUUID(),
      ),
    )
    val calculation: UnusedDeductionCalculationResponse = webTestClient.post()
      .uri("/unused-deductions/UNUSED/calculation")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(adjustments))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(UnusedDeductionCalculationResponse::class.java)
      .returnResult().responseBody!!

    Assertions.assertThat(calculation.unusedDeductions).isEqualTo(305)
  }

  @Test
  fun `Run unused deductions calculation (not enough deductions)`() {
    val adjustments = listOf(
      AdjustmentServiceAdjustment(
        fromDate = LocalDate.of(2020, 2, 1),
        toDate = LocalDate.of(2021, 1, 31),
        daysTotal = 10,
        effectiveDays = 10,
        bookingId = "UNUSED".hashCode().toLong(),
        sentenceSequence = 4,
        adjustmentType = AdjustmentServiceAdjustmentType.REMAND,
        person = "UNUSED",
        id = UUID.randomUUID(),
      ),
    )
    val calculation: UnusedDeductionCalculationResponse = webTestClient.post()
      .uri("/unused-deductions/UNUSED/calculation")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(adjustments))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(UnusedDeductionCalculationResponse::class.java)
      .returnResult().responseBody!!

    Assertions.assertThat(calculation.unusedDeductions).isEqualTo(0)
  }

  @Test
  fun `Run unused deductions calculation returning validation messages for remand overlapping remand`() {
    val adjustments = listOf(
      AdjustmentServiceAdjustment(
        fromDate = LocalDate.of(2020, 2, 1),
        toDate = LocalDate.of(2021, 1, 31),
        daysTotal = 396,
        effectiveDays = 396,
        bookingId = "UNUSED".hashCode().toLong(),
        sentenceSequence = 4,
        adjustmentType = AdjustmentServiceAdjustmentType.REMAND,
        person = "UNUSED",
        id = UUID.randomUUID(),
      ),
      AdjustmentServiceAdjustment(
        fromDate = LocalDate.of(2020, 5, 1),
        toDate = LocalDate.of(2021, 6, 1),
        daysTotal = 31,
        effectiveDays = 31,
        bookingId = "UNUSED".hashCode().toLong(),
        sentenceSequence = 4,
        adjustmentType = AdjustmentServiceAdjustmentType.REMAND,
        person = "UNUSED",
        id = UUID.randomUUID(),
      ),
    )
    val calculation: UnusedDeductionCalculationResponse = webTestClient.post()
      .uri("/unused-deductions/UNUSED/calculation")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(adjustments))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(UnusedDeductionCalculationResponse::class.java)
      .returnResult().responseBody!!

    Assertions.assertThat(calculation.validationMessages).contains(ValidationMessage(ValidationCode.REMAND_OVERLAPS_WITH_REMAND, arguments = listOf(adjustments[1].fromDate.toString(), adjustments[1].toDate.toString(), adjustments[0].fromDate.toString(), adjustments[0].toDate.toString())))
  }

  @Test
  fun `Run unused deductions calculation returning validation messages for remand overlapping sentence`() {
    val adjustments = listOf(
      AdjustmentServiceAdjustment(
        fromDate = LocalDate.of(2021, 1, 1),
        toDate = LocalDate.of(2021, 2, 20),
        daysTotal = 50,
        effectiveDays = 50,
        bookingId = "UNUSED".hashCode().toLong(),
        sentenceSequence = 4,
        adjustmentType = AdjustmentServiceAdjustmentType.REMAND,
        person = "UNUSED",
        id = UUID.randomUUID(),
      ),
    )
    val calculation: UnusedDeductionCalculationResponse = webTestClient.post()
      .uri("/unused-deductions/UNUSED/calculation")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(adjustments))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(UnusedDeductionCalculationResponse::class.java)
      .returnResult().responseBody!!

    Assertions.assertThat(calculation.validationMessages).contains(ValidationMessage(ValidationCode.REMAND_OVERLAPS_WITH_SENTENCE, arguments = listOf("2021-02-01", "2021-03-13", adjustments[0].fromDate.toString(), adjustments[0].toDate.toString())))
  }

  @Test
  fun `Run unused deductions calculation where there is a later sentence date than the one producing the release date`() {
    val adjustments = listOf(
      AdjustmentServiceAdjustment(
        fromDate = null,
        toDate = null,
        bookingId = "UNUSED-C".hashCode().toLong(),
        sentenceSequence = 2,
        adjustmentType = AdjustmentServiceAdjustmentType.TAGGED_BAIL,
        daysTotal = 70,
        effectiveDays = 70,
        person = "UNUSED-C",
        id = UUID.randomUUID(),
      ),
    )
    val calculation: UnusedDeductionCalculationResponse = webTestClient.post()
      .uri("/unused-deductions/UNUSED-C/calculation")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(adjustments))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(UnusedDeductionCalculationResponse::class.java)
      .returnResult().responseBody!!

    Assertions.assertThat(calculation.unusedDeductions).isEqualTo(9)
  }

  @Test
  fun `Run unused deductions calculation where there is a later sentence date than the one producing the release date (not enough adjustment)`() {
    val adjustments = listOf(
      AdjustmentServiceAdjustment(
        fromDate = null,
        toDate = null,
        bookingId = "UNUSED-C".hashCode().toLong(),
        sentenceSequence = 2,
        adjustmentType = AdjustmentServiceAdjustmentType.TAGGED_BAIL,
        daysTotal = 10,
        effectiveDays = 10,
        person = "UNUSED-C",
        id = UUID.randomUUID(),
      ),
    )
    val calculation: UnusedDeductionCalculationResponse = webTestClient.post()
      .uri("/unused-deductions/UNUSED-C/calculation")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(adjustments))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(UnusedDeductionCalculationResponse::class.java)
      .returnResult().responseBody!!

    Assertions.assertThat(calculation.unusedDeductions).isEqualTo(0)
  }
}
