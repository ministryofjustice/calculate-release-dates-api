package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.controller

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UnusedDeductionCalculationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate
import java.util.UUID

class UnusedDeductionsControllerIntTest(private val mockManageOffencesClient: MockManageOffencesClient) : IntegrationTestBase() {

  @Test
  fun `Run unused deductions calculation`() {
    mockManageOffencesClient.noneInPCSC(listOf("TH68010A", "TH68037"))
    val adjustments = listOf(
      AdjustmentDto(
        fromDate = LocalDate.of(2020, 2, 1),
        toDate = LocalDate.of(2021, 1, 31),
        days = 396,
        effectiveDays = 396,
        bookingId = "UNUSED".hashCode().toLong(),
        sentenceSequence = 4,
        adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
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
      AdjustmentDto(
        fromDate = LocalDate.of(2020, 2, 1),
        toDate = LocalDate.of(2021, 1, 31),
        days = 10,
        effectiveDays = 10,
        bookingId = "UNUSED".hashCode().toLong(),
        sentenceSequence = 4,
        adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
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
      AdjustmentDto(
        fromDate = LocalDate.of(2020, 2, 1),
        toDate = LocalDate.of(2021, 1, 31),
        days = 396,
        effectiveDays = 396,
        bookingId = "UNUSED".hashCode().toLong(),
        sentenceSequence = 4,
        adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
        person = "UNUSED",
        id = UUID.randomUUID(),
      ),
      AdjustmentDto(
        fromDate = LocalDate.of(2020, 5, 1),
        toDate = LocalDate.of(2021, 6, 1),
        days = 31,
        effectiveDays = 31,
        bookingId = "UNUSED".hashCode().toLong(),
        sentenceSequence = 4,
        adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
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

    Assertions.assertThat(calculation.validationMessages).contains(ValidationMessage(ValidationCode.REMAND_OVERLAPS_WITH_REMAND, arguments = listOf(adjustments[0].fromDate.toString(), adjustments[0].toDate.toString(), adjustments[1].fromDate.toString(), adjustments[1].toDate.toString())))
  }

  @Test
  fun `Run unused deductions calculation returning validation messages for remand overlapping sentence`() {
    val adjustments = listOf(
      AdjustmentDto(
        fromDate = LocalDate.of(2021, 1, 1),
        toDate = LocalDate.of(2021, 2, 20),
        days = 50,
        effectiveDays = 50,
        bookingId = "UNUSED".hashCode().toLong(),
        sentenceSequence = 4,
        adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
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

    Assertions.assertThat(calculation.validationMessages).contains(ValidationMessage(ValidationCode.REMAND_ON_OR_AFTER_SENTENCE_DATE, arguments = listOf("2", "2")))
  }

  @Test
  fun `Run unused deductions calculation returning validation messages for remand overlapping sentence remand starts after sentence date`() {
    val adjustments = listOf(
      AdjustmentDto(
        fromDate = LocalDate.of(2021, 2, 2),
        toDate = LocalDate.of(2021, 2, 20),
        days = 12,
        effectiveDays = 12,
        bookingId = "UNUSED".hashCode().toLong(),
        sentenceSequence = 4,
        adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
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

    Assertions.assertThat(calculation.validationMessages).contains(ValidationMessage(ValidationCode.REMAND_ON_OR_AFTER_SENTENCE_DATE, arguments = listOf("2", "2")))
  }

  @Test
  fun `Run unused deductions calculation where there is a later sentence date than the one producing the release date`() {
    val adjustments = listOf(
      AdjustmentDto(
        fromDate = null,
        toDate = null,
        bookingId = "UNUSED-C".hashCode().toLong(),
        sentenceSequence = 2,
        adjustmentType = AdjustmentDto.AdjustmentType.TAGGED_BAIL,
        days = 70,
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
      AdjustmentDto(
        fromDate = null,
        toDate = null,
        bookingId = "UNUSED-C".hashCode().toLong(),
        sentenceSequence = 2,
        adjustmentType = AdjustmentDto.AdjustmentType.TAGGED_BAIL,
        days = 10,
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

  @Test
  fun `Run unused deductions calculation with unsupported adjustment type`() {
    val adjustments = listOf(
      AdjustmentDto(
        fromDate = LocalDate.of(2020, 2, 1),
        toDate = LocalDate.of(2021, 1, 31),
        days = 396,
        effectiveDays = 396,
        bookingId = "UNUSED".hashCode().toLong(),
        sentenceSequence = 4,
        adjustmentType = AdjustmentDto.AdjustmentType.LAWFULLY_AT_LARGE,
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

    Assertions.assertThat(calculation.validationMessages).contains(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE))
  }

  @Test
  fun `Run unused deductions calculation with an SDS40 early release`() {
    val adjustments = listOf(
      AdjustmentDto(
        fromDate = LocalDate.of(2020, 2, 1),
        toDate = LocalDate.of(2021, 1, 31),
        days = 60,
        effectiveDays = 60,
        bookingId = "UNUSED-E".hashCode().toLong(),
        sentenceSequence = 4,
        adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
        person = "UNUSED-E",
        id = UUID.randomUUID(),
      ),
    )

    val calculation: UnusedDeductionCalculationResponse = webTestClient.post()
      .uri("/unused-deductions/UNUSED-E/calculation")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(adjustments))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(UnusedDeductionCalculationResponse::class.java)
      .returnResult().responseBody!!

    Assertions.assertThat(calculation.unusedDeductions).isEqualTo(20)
  }

  @Test
  fun `Release in-between sentences unused deductions calculation  (ADJST-1363)`() {
    mockManageOffencesClient.noneInPCSC(listOf("CS00011", "WR91001"))

    val adjustments = listOf(
      AdjustmentDto(
        fromDate = LocalDate.of(2024, 2, 2),
        toDate = LocalDate.of(2024, 5, 13),
        days = 102,
        effectiveDays = 102,
        bookingId = "UNUSED-F".hashCode().toLong(),
        sentenceSequence = 1,
        adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
        person = "UNUSED-F",
        id = UUID.randomUUID(),
      ),
      AdjustmentDto(
        fromDate = LocalDate.of(2025, 6, 16),
        toDate = LocalDate.of(2025, 8, 31),
        days = 77,
        effectiveDays = 77,
        bookingId = "UNUSED-F".hashCode().toLong(),
        sentenceSequence = 2,
        adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
        person = "UNUSED-F",
        id = UUID.randomUUID(),
      ),
    )

    val calculation: UnusedDeductionCalculationResponse = webTestClient.post()
      .uri("/unused-deductions/UNUSED-F/calculation")
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
  fun `Release in-between sentences unused deductions calculation (ADJST-1373)`() {
    mockManageOffencesClient.noneInPCSC(listOf("CS00011", "PA53052", "WR91001"))

    val adjustments = listOf(
      AdjustmentDto(
        fromDate = LocalDate.of(2024, 7, 19),
        toDate = LocalDate.of(2024, 7, 30),
        days = 12,
        effectiveDays = 12,
        bookingId = "UNUSED-G".hashCode().toLong(),
        sentenceSequence = 1,
        adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
        person = "UNUSED-G",
        id = UUID.randomUUID(),
      ),
      AdjustmentDto(
        fromDate = LocalDate.of(2025, 5, 24),
        toDate = LocalDate.of(2025, 8, 4),
        days = 73,
        effectiveDays = 73,
        bookingId = "UNUSED-G".hashCode().toLong(),
        sentenceSequence = 1,
        adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
        person = "UNUSED-G",
        id = UUID.randomUUID(),
      ),
      AdjustmentDto(
        fromDate = LocalDate.of(2025, 8, 10),
        toDate = LocalDate.of(2025, 8, 28),
        days = 19,
        effectiveDays = 19,
        bookingId = "UNUSED-G".hashCode().toLong(),
        sentenceSequence = 10,
        adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
        person = "UNUSED-G",
        id = UUID.randomUUID(),
      ),
    )

    val calculation: UnusedDeductionCalculationResponse = webTestClient.post()
      .uri("/unused-deductions/UNUSED-G/calculation")
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
