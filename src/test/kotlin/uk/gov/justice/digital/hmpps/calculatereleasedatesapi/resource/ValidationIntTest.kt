package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.PcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_AFTER_RELEASE_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_AFTER_RELEASE_RADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_RADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_UAL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_WITH_PAYMENTS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.BROKEN_CONSECUTIVE_CHAINS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.CONCURRENT_CONSECUTIVE_SENTENCES_DURATION
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_RANGE_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_START_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.OFFENCE_MISSING_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.PRISONER_SUBJECT_TO_PTD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_FROM_TO_DATES_REQUIRED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_OVERLAPS_WITH_REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SEC_91_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SENTENCE_HAS_MULTIPLE_TERMS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_CALCULATION_DTO_WITH_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_DTO_RECALL_SEC104_SEC105
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_SENTENCE_TYPE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ZERO_IMPRISONMENT_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage

class ValidationIntTest(private val mockManageOffencesClient: MockManageOffencesClient) : IntegrationTestBase() {
  @Test
  fun `Run calculation where remand periods overlap with a sentence period`() {
    runValidationAndCheckMessages(
      REMAND_OVERLAPS_WITH_SENTENCE_PRISONER_ID,
      listOf(
        ValidationMessage(
          ValidationCode.REMAND_OVERLAPS_WITH_SENTENCE,
          arguments = listOf("2000-04-29", "2001-02-23", "2000-04-28", "2000-04-30"),
        ),
      ),
    )
  }

  @Test
  fun `Run validation for DTO`() {
    runValidationAndCheckMessages("CRS-1184", emptyList())
  }

  @Test
  fun `Run validation for DTO concurrent to recall`() {
    mockManageOffencesClient.noneInPCSC(listOf("CD71040", "CJ88117"))
    runValidationAndCheckMessages("CRS-1145-AC1", listOf(ValidationMessage(code = UNSUPPORTED_CALCULATION_DTO_WITH_RECALL)))
  }

  @Test
  fun `Run validation on future dated adjustments`() {
    mockManageOffencesClient.noneInPCSC(listOf("CD98075", "CJ88144", "CJ88148", "OF61016", "TH68037"))
    runValidationAndCheckMessages("CRS-1044", listOf(ValidationMessage(ADJUSTMENT_FUTURE_DATED_RADA)))
  }

  @Test
  fun `Run validation for overlapping remand and custodial period`() {
    mockManageOffencesClient.noneInPCSC(listOf("MD71526", "MD71530", "MD71533", "PC02021"))
    runValidationAndCheckMessages("CRS-1394", listOf())
  }

  @Test
  fun `Run validation on invalid data`() {
    mockManageOffencesClient.noneInPCSC(listOf("GBH", "SX03014"))
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
        ValidationMessage(code = CONCURRENT_CONSECUTIVE_SENTENCES_DURATION, arguments = listOf("3", "0", "0", "0")),
        ValidationMessage(code = REMAND_FROM_TO_DATES_REQUIRED),
        ValidationMessage(code = REMAND_FROM_TO_DATES_REQUIRED),
        ValidationMessage(code = ADJUSTMENT_FUTURE_DATED_ADA),
        ValidationMessage(code = ADJUSTMENT_FUTURE_DATED_RADA),
        ValidationMessage(code = ADJUSTMENT_FUTURE_DATED_UAL),
        ValidationMessage(code = REMAND_OVERLAPS_WITH_REMAND, arguments = listOf("2000-03-28", "2000-04-28", "2000-04-01", "2000-04-02")),
      ),
    )
  }

  @Test
  fun `Run validation on DTO with unsupported term type`() {
    runValidationAndCheckMessages(
      "DTO_NON_IMP",
      listOf(
        ValidationMessage(code = UNSUPPORTED_DTO_RECALL_SEC104_SEC105),

      ),
    )
  }

  @Test
  fun `Run validation on unsupported sentence data`() {
    runValidationAndCheckMessages(
      UNSUPPORTED_SENTENCE_PRISONER_ID,
      listOf(ValidationMessage(UNSUPPORTED_SENTENCE_TYPE, listOf("2003", "This sentence is unsupported"))),
    )
  }

  @Test
  fun `Run supported validation on unsupported sentence data`() {
    runSupportedValidationAndCheckMessages(
      UNSUPPORTED_SENTENCE_PRISONER_ID,
      listOf(ValidationMessage(UNSUPPORTED_SENTENCE_TYPE, listOf("2003", "This sentence is unsupported"))),
    )
  }

  @Test
  fun `Run supported validation for unsupported DTO concurrent to recall`() {
    runSupportedValidationAndCheckMessages("CRS-1145-AC1", listOf(ValidationMessage(code = UNSUPPORTED_CALCULATION_DTO_WITH_RECALL)))
  }

  @Test
  fun `Run validation on valid data`() {
    runValidationAndCheckMessages(PRISONER_ID, emptyList())
  }

  @Test
  fun `Run validation on inactive data not included`() {
    runValidationAndCheckMessages(INACTIVE_PRISONER_ID, emptyList())
  }

  @Test
  fun `Run validation on inactive data included`() {
    runValidationAndCheckMessages(INACTIVE_PRISONER_ID, listOf(ValidationMessage(OFFENCE_MISSING_DATE, arguments = listOf("1", "3"))), true)
  }

  @Test
  fun `Run validation on extinguished crd booking`() {
    mockManageOffencesClient.noneInPCSC(listOf("CT94012", "TH68003A", "TH68045"))
    runValidationAndCheckMessages(
      "EXTINGUISH",
      listOf(
        ValidationMessage(ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED_REMAND),
      ),
    )
  }

  @Test
  fun `Run validation on unsupported prisoner data`() {
    runValidationAndCheckMessages(UNSUPPORTED_PRISONER_PRISONER_ID, listOf(ValidationMessage(PRISONER_SUBJECT_TO_PTD)))
  }

  @Test
  fun `Run validation on on prisoner with fine payment`() {
    runValidationAndCheckMessages("PAYMENTS", listOf(ValidationMessage(A_FINE_SENTENCE_WITH_PAYMENTS)))
  }

  @Test
  fun `Run validation on adjustment after release date 1`() {
    mockManageOffencesClient.noneInPCSC(listOf("MD71134", "MD71191"))
    runValidationAndCheckMessages(
      "CRS-796-1",
      listOf(
        ValidationMessage(ADJUSTMENT_AFTER_RELEASE_ADA),
        ValidationMessage(ADJUSTMENT_AFTER_RELEASE_RADA),
      ),
    )
  }

  @Test
  fun `Run validation on adjustment after release date 1 with more RADA and ADA`() {
    mockManageOffencesClient.noneInPCSC(listOf("MD71134", "MD71191"))
    runValidationAndCheckMessages(
      "CRS-796-1-more-adas-radas",
      listOf(
        ValidationMessage(ADJUSTMENT_AFTER_RELEASE_ADA),
        ValidationMessage(ADJUSTMENT_AFTER_RELEASE_RADA),
      ),
    )
  }

  @Test
  fun `Run validation on adjustment after release date 2`() {
    mockManageOffencesClient.noneInPCSC(listOf("CD71040", "FI68247", "FI68410"))
    runValidationAndCheckMessages("CRS-796-2", listOf(ValidationMessage(ADJUSTMENT_AFTER_RELEASE_ADA)))
  }

  @Test
  fun `Run validation on adjustment after release with a term`() {
    mockManageOffencesClient.noneInPCSC(listOf("RT88333", "TH68013"))
    runValidationAndCheckMessages("CRS-1191-1", listOf(ValidationMessage(ADJUSTMENT_AFTER_RELEASE_ADA)))
  }

  @Test
  fun `Run validation on rada after ada adjustment extends release date`() {
    mockManageOffencesClient.withPCSCMarkersResponse(
      OffencePcscMarkers(
        offenceCode = "TR68132",
        pcscMarkers = PcscMarkers(inListA = true, inListB = true, inListC = true, inListD = true),
      ),
      offences = "TR68132",
    )

    runValidationAndCheckMessages("CRS-2151", emptyList())
  }

  @Test
  fun `Run validate for broken consecutive chain`() {
    runValidationAndCheckMessages(
      BROKEN_CONSECUTIVE_CHAIN_SENTENCE,
      listOf(ValidationMessage(BROKEN_CONSECUTIVE_CHAINS)),
    )
  }

  @Test
  fun `Run validate for manual entry missing offence start date`() {
    runValidateForManualEntry(
      MISSING_OFFENCE_START_DATE_SENTENCE,
      listOf(ValidationMessage(OFFENCE_MISSING_DATE, listOf("1", "1"))),
    )
  }

  @Test
  fun `Run validation on zero day adjustment`() {
    runValidationAndCheckMessages("ZERO", emptyList())
  }

  @Test
  fun `Run validation for multiple sentences consecutive to the same parent`() {
    mockManageOffencesClient.noneInPCSC(listOf("AW06005B", "HP25001", "PU86003B", "RF96105", "TM94004B"))
    runValidationAndCheckMessages(
      "CRS-2283-1",
      listOf(
        ValidationMessage(ValidationCode.CONCURRENT_CONSECUTIVE_SENTENCES_DURATION, listOf("0", "12", "0", "0")),
      ),
    )

    mockManageOffencesClient.noneInPCSC(listOf("CJ91015", "DX56013A", "PH98001", "RD78005", "SZ07011"))
    runValidationAndCheckMessages(
      "CRS-2283-2",
      listOf(
        ValidationMessage(ValidationCode.CONCURRENT_CONSECUTIVE_SENTENCES_DURATION, listOf("0", "3", "1", "0")),
      ),
    )

    mockManageOffencesClient.noneInPCSC(listOf("CJ91015", "DX56013A", "PH98001", "RD78005", "SZ07011"))
    runValidationAndCheckMessages(
      "CRS-2283-3",
      emptyList(),
    )
  }

  private fun runSupportedValidationAndCheckMessages(prisonerId: String, messages: List<ValidationMessage>) {
    val validationMessages = webTestClient.get()
      .uri("/validation/$prisonerId/supported-validation")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBodyList(ValidationMessage::class.java)
      .returnResult().responseBody!!

    assertThat(validationMessages).isEqualTo(messages)
  }

  private fun runValidateForManualEntry(prisonerId: String, messages: List<ValidationMessage>) {
    val validationMessages = webTestClient.get()
      .uri("/validation/$prisonerId/manual-entry-validation")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBodyList(ValidationMessage::class.java)
      .returnResult().responseBody!!

    assertThat(validationMessages).isEqualTo(messages)
  }

  private fun runValidationAndCheckMessages(prisonerId: String, messages: List<ValidationMessage>, includeInactiveData: Boolean? = null) {
    val validationMessages = webTestClient.post()
      .uri("/validation/$prisonerId/full-validation" + if (includeInactiveData == true) "?includeInactiveData=true" else "")
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
    const val MISSING_OFFENCE_START_DATE_SENTENCE = "CRS-1634-no-offence-start-date"
    const val BROKEN_CONSECUTIVE_CHAIN_SENTENCE = "CRS-2338"
  }
}
