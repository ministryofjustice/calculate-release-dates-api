package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_AFTER_RELEASE_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_RADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_UAL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_WITH_PAYMENTS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.MULTIPLE_SENTENCES_CONSECUTIVE_TO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_RANGE_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_START_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.OFFENCE_MISSING_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.PRISONER_SUBJECT_TO_PTD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_FROM_TO_DATES_REQUIRED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_OVERLAPS_WITH_REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_OVERLAPS_WITH_SENTENCE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SEC_91_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SENTENCE_HAS_MULTIPLE_TERMS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_SENTENCE_TYPE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ZERO_IMPRISONMENT_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage

class ValidationIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var objectMapper: ObjectMapper

  @Test
  fun `Run calculation where remand periods overlap with a sentence period`() {
    runValidationAndCheckMessages(
      REMAND_OVERLAPS_WITH_SENTENCE_PRISONER_ID,
      listOf(ValidationMessage(REMAND_OVERLAPS_WITH_SENTENCE))
    )
  }

  @Test
  fun `Run validation on future dated adjustments`() {
    runValidationAndCheckMessages("CRS-1044", listOf(ValidationMessage(ADJUSTMENT_FUTURE_DATED_RADA)))
  }

  @Test
  fun `Run validation on invalid data`() {
    runValidationAndCheckMessages(
      VALIDATION_PRISONER_ID,
      listOf(
        ValidationMessage(code = OFFENCE_DATE_AFTER_SENTENCE_START_DATE, arguments = listOf("1", "1")),
        ValidationMessage(code = OFFENCE_DATE_AFTER_SENTENCE_RANGE_DATE, arguments = listOf("1", "2")),
        ValidationMessage(code = OFFENCE_MISSING_DATE, arguments = listOf("2", "1")),
        ValidationMessage(code = ZERO_IMPRISONMENT_TERM, arguments = listOf("2", "1")),
        ValidationMessage(code = SENTENCE_HAS_MULTIPLE_TERMS, arguments = listOf("2", "2")),
        ValidationMessage(code = SEC_91_SENTENCE_TYPE_INCORRECT, arguments = listOf("2", "4")),
        ValidationMessage(code = SEC_91_SENTENCE_TYPE_INCORRECT, arguments = listOf("2", "4")),
        ValidationMessage(code = MULTIPLE_SENTENCES_CONSECUTIVE_TO, arguments = listOf("2", "2")),
        ValidationMessage(code = REMAND_FROM_TO_DATES_REQUIRED),
        ValidationMessage(code = REMAND_FROM_TO_DATES_REQUIRED),
        ValidationMessage(code = ADJUSTMENT_FUTURE_DATED_ADA),
        ValidationMessage(code = ADJUSTMENT_FUTURE_DATED_RADA),
        ValidationMessage(code = ADJUSTMENT_FUTURE_DATED_UAL),
        ValidationMessage(code = REMAND_OVERLAPS_WITH_REMAND)
      )
    )
  }

  @Test
  fun `Run validation on unsupported sentence data`() {
    runValidationAndCheckMessages(
      UNSUPPORTED_SENTENCE_PRISONER_ID,
      listOf(ValidationMessage(UNSUPPORTED_SENTENCE_TYPE, listOf("2003", "This sentence is unsupported")))
    )
  }

  @Test
  fun `Run validation on valid data`() {
    runValidationAndCheckMessages(PRISONER_ID, emptyList())
  }

  @Test
  fun `Run validation on inactive data`() {
    runValidationAndCheckMessages(INACTIVE_PRISONER_ID, emptyList())
  }

  @Test
  fun `Run validation on extinguished crd booking`() {
    runValidationAndCheckMessages(
      "EXTINGUISH",
      listOf(
        ValidationMessage(ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED_REMAND)
      )
    )
  }

  @Test
  fun `Run validation on argument after release date 1`() {
    runValidationAndCheckMessages("CRS-796-1", listOf(ValidationMessage(ADJUSTMENT_AFTER_RELEASE_ADA)))
  }

  @Test
  fun `Run validation on argument after release date 2`() {
    runValidationAndCheckMessages("CRS-796-2", listOf(ValidationMessage(ADJUSTMENT_AFTER_RELEASE_ADA)))
  }

  @Test
  fun `Run validation on unsupported prisoner data`() {
    runValidationAndCheckMessages(UNSUPPORTED_PRISONER_PRISONER_ID, listOf(ValidationMessage(PRISONER_SUBJECT_TO_PTD)))
  }

  @Test
  fun `Run validation on on prisoner with fine payment`() {
    runValidationAndCheckMessages("PAYMENTS", listOf(ValidationMessage(A_FINE_SENTENCE_WITH_PAYMENTS)))
  }

  private fun runValidationAndCheckMessages(prisonerId: String, messages: List<ValidationMessage>) {
    val validationMessages = webTestClient.post()
      .uri("/validation/$prisonerId/full-validation")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBodyList(ValidationMessage::class.java)
      .returnResult().responseBody!!

    assertThat(validationMessages).isEqualTo(messages)
  }

  companion object {
    const val PRISONER_ID = "default"
    const val REMAND_OVERLAPS_WITH_SENTENCE_PRISONER_ID = "REM-SEN"
    const val VALIDATION_PRISONER_ID = "VALIDATION"
    const val UNSUPPORTED_SENTENCE_PRISONER_ID = "UNSUPP_SENT"
    const val UNSUPPORTED_PRISONER_PRISONER_ID = "UNSUPP_PRIS"
    const val INACTIVE_PRISONER_ID = "INACTIVE"
  }
}