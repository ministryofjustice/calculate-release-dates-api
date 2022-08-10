package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SentencesExtractionService
import java.time.LocalDate

class ValidationServiceTest {

  private val featureToggles = FeatureToggles(eds = true, sopc = true)
  private val validationService = ValidationService(featureToggles, SentencesExtractionService())
  private val offences = listOf(
    OffenderOffence(
      offenderChargeId = 1L,
      offenceStartDate = FIRST_MAY_2020,
      offenceCode = "ABC",
      offenceDescription = "Littering",
      indicators = listOf(OffenderOffence.SCHEDULE_15_LIFE_INDICATOR)
    ),
  )
  private val validEdsSentence = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 7,
    lineSequence = 2,
    caseSequence = 1,
    sentenceDate = FIRST_MAY_2018,
    terms = listOf(
      SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      SentenceTerms(2, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
    ),
    sentenceCalculationType = SentenceCalculationType.LASPO_DR.name,
    sentenceStatus = "a",
    sentenceCategory = "a",
    sentenceTypeDescription = "a",
    offences = listOf(),
  )
  private val validSopcSentence = validEdsSentence.copy(
    terms = listOf(
      SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      SentenceTerms(1, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
    ),
    sentenceCalculationType = SentenceCalculationType.SOPC21.name,
    sentenceDate = FIRST_MAY_2021
  )
  private val validPrisoner = PrisonerDetails(offenderNo = "", bookingId = 1, dateOfBirth = LocalDate.of(1, 2, 3))
  private val validAdjustments = BookingAndSentenceAdjustments(emptyList(), emptyList())

  @Test
  fun `Test EDS valid sentence should pass`() {
    val result = validationService.validate(PrisonApiSourceData(listOf(validEdsSentence), validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.VALID)
    assertThat(result.messages).hasSize(0)
  }

  @Test
  fun `Test EDS sentences should have imprisonment term`() {
    val sentence = validEdsSentence.copy(
      terms = listOf(
        SentenceTerms(2, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
      ),
    )
    val result = validationService.validate(PrisonApiSourceData(listOf(sentence), validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.VALIDATION)
    assertThat(result.messages).hasSize(1)
    assertThat(result.messages[0].code).isEqualTo(ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM)
  }

  @Test
  fun `Test EDS sentences should have imprisonment term with some duration`() {
    val sentence = validEdsSentence.copy(
      terms = listOf(
        SentenceTerms(0, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
        SentenceTerms(2, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
      ),
    )
    val result = validationService.validate(PrisonApiSourceData(listOf(sentence), validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.VALIDATION)
    assertThat(result.messages).hasSize(1)
    assertThat(result.messages[0].code).isEqualTo(ValidationCode.ZERO_IMPRISONMENT_TERM)
  }

  @Test
  fun `Test EDS sentences should have license term`() {
    val sentence = validEdsSentence.copy(
      terms = listOf(
        SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE)
      ),
    )
    val result = validationService.validate(PrisonApiSourceData(listOf(sentence), validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.VALIDATION)
    assertThat(result.messages).hasSize(1)
    assertThat(result.messages[0].code).isEqualTo(ValidationCode.SENTENCE_HAS_NO_LICENCE_TERM)
  }

  @Test
  fun `Test EDS sentences should have license term of at least 1 year`() {
    val sentences = listOf(
      validEdsSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(0, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE)
        ),
      ),
      validEdsSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(0, 11, 0, 0, SentenceTerms.LICENCE_TERM_CODE)
        ),
      )
    )
    val result = validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.VALIDATION)
    assertThat(result.messages).hasSize(2)
    assertThat(result.messages[0].code).isEqualTo(ValidationCode.EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR)
    assertThat(result.messages[1].code).isEqualTo(ValidationCode.EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR)
  }

  @Test
  fun `Test EDS sentences should have license term of at least 1 year valid`() {
    val sentences = listOf(
      validEdsSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(0, 11, 5, 0, SentenceTerms.LICENCE_TERM_CODE)
        ),
      ),
      validEdsSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(0, 0, 0, 377, SentenceTerms.LICENCE_TERM_CODE)
        ),
      )
    )
    val result = validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.VALID)
  }

  @Test
  fun `Test EDS sentences should have license term of at less than 8 years`() {
    val sentences = listOf(
      validEdsSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(8, 0, 0, 1, SentenceTerms.LICENCE_TERM_CODE)
        ),
      )
    )
    val result = validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.VALIDATION)
    assertThat(result.messages).hasSize(1)
    assertThat(result.messages[0].code).isEqualTo(ValidationCode.EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS)
  }

  @Test
  fun `Test EDSXXX sentences should be correctly dated`() {
    val sentences = listOf(
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.EDS18.name,
        sentenceDate = ImportantDates.EDS18_SENTENCE_TYPES_START_DATE.minusDays(1)
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.EDSU18.name,
        sentenceDate = ImportantDates.EDS18_SENTENCE_TYPES_START_DATE.minusDays(1)
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.EDS21.name,
        sentenceDate = ImportantDates.EDS18_SENTENCE_TYPES_START_DATE.minusDays(1)
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.LASPO_DR.name,
        sentenceDate = ImportantDates.EDS18_SENTENCE_TYPES_START_DATE.minusDays(1)
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.EDS18.name,
        sentenceDate = ImportantDates.EDS18_SENTENCE_TYPES_START_DATE.plusDays(1)
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.EDSU18.name,
        sentenceDate = ImportantDates.EDS18_SENTENCE_TYPES_START_DATE.plusDays(1)
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.EDS21.name,
        sentenceDate = ImportantDates.EDS18_SENTENCE_TYPES_START_DATE.plusDays(1)
      ),
    )
    val result = validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.VALIDATION)
    assertThat(result.messages).hasSize(3)
    assertThat(result.messages[0].code).isEqualTo(ValidationCode.EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT)
    assertThat(result.messages[1].code).isEqualTo(ValidationCode.EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT)
    assertThat(result.messages[2].code).isEqualTo(ValidationCode.EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT)
  }

  @Test
  fun `Test LASPO_AR sentences should be correctly dated`() {
    val sentences = listOf(
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.LASPO_AR.name,
        sentenceDate = ImportantDates.LASPO_AR_SENTENCE_TYPES_END_DATE.plusDays(1)
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.LASPO_DR.name,
        sentenceDate = ImportantDates.LASPO_AR_SENTENCE_TYPES_END_DATE.minusDays(1)
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.LASPO_AR.name,
        sentenceDate = ImportantDates.LASPO_AR_SENTENCE_TYPES_END_DATE.minusDays(1)
      )
    )
    val result = validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.VALIDATION)
    assertThat(result.messages).hasSize(1)
    assertThat(result.messages[0].code).isEqualTo(ValidationCode.LASPO_AR_SENTENCE_TYPE_INCORRECT)
  }

  @Test
  fun `Test EDS sentences shouldnt have more than one license term or imprisonment term`() {
    val sentences = listOf(
      validEdsSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(2, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
        ),
      ),
      validEdsSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(2, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
          SentenceTerms(2, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
        ),
      )
    )
    val result = validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.VALIDATION)
    assertThat(result.messages).hasSize(2)
    assertThat(result.messages[0].code).isEqualTo(ValidationCode.MORE_THAN_ONE_IMPRISONMENT_TERM)
    assertThat(result.messages[1].code).isEqualTo(ValidationCode.MORE_THAN_ONE_LICENCE_TERM)
  }

  @Test
  fun `Test EDS sentences should be unsupported if feature toggle disabled`() {
    val featureToggles = FeatureToggles(eds = false, sopc = true)
    val validationService = ValidationService(featureToggles, SentencesExtractionService())
    val sentences = listOf(validEdsSentence)
    val result = validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.UNSUPPORTED)
    assertThat(result.messages).hasSize(1)
    assertThat(result.messages[0].code).isEqualTo(ValidationCode.UNSUPPORTED_SENTENCE_TYPE)
  }
  @Test
  fun `Test SOPC sentences should be unsupported if feature toggle disabled`() {
    val featureToggles = FeatureToggles(eds = true, sopc = false)
    val validationService = ValidationService(featureToggles, SentencesExtractionService())
    val sentences = listOf(validSopcSentence)
    val result = validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.UNSUPPORTED)
    assertThat(result.messages).hasSize(1)
    assertThat(result.messages[0].code).isEqualTo(ValidationCode.UNSUPPORTED_SENTENCE_TYPE)
  }
  @Test
  fun `Test SOPC valid sentence should pass`() {
    val result = validationService.validate(PrisonApiSourceData(listOf(validSopcSentence), validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.VALID)
    assertThat(result.messages).hasSize(0)
  }

  @Test
  fun `Test SOPC18 SOPC21 sentences should be correctly dated`() {
    val sentences = listOf(
      validSopcSentence.copy(
        sentenceCalculationType = SentenceCalculationType.SOPC18.name,
        sentenceDate = ImportantDates.SEC_91_END_DATE.minusDays(1)
      ),
      validSopcSentence.copy(
        sentenceCalculationType = SentenceCalculationType.SOPC18.name,
        sentenceDate = ImportantDates.SEC_91_END_DATE
      ),
      validSopcSentence.copy(
        sentenceCalculationType = SentenceCalculationType.SOPC21.name,
        sentenceDate = ImportantDates.SEC_91_END_DATE.minusDays(1)
      ),
      validSopcSentence.copy(
        sentenceCalculationType = SentenceCalculationType.SOPC21.name,
        sentenceDate = ImportantDates.SEC_91_END_DATE
      ),
    )
    val result = validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.VALIDATION)
    assertThat(result.messages).hasSize(2)
    assertThat(result.messages[0].code).isEqualTo(ValidationCode.SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT)
    assertThat(result.messages[1].code).isEqualTo(ValidationCode.SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT)
  }
  @Test
  fun `Test SEC236A sentences should be correctly dated`() {
    val sentences = listOf(
      validSopcSentence.copy(
        sentenceCalculationType = SentenceCalculationType.SEC236A.name,
        sentenceDate = ImportantDates.SEC_91_END_DATE
      ),
      validSopcSentence.copy(
        sentenceCalculationType = SentenceCalculationType.SEC236A.name,
        sentenceDate = ImportantDates.SEC_91_END_DATE.minusDays(1)
      ),
    )
    val result = validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.VALIDATION)
    assertThat(result.messages).hasSize(1)
    assertThat(result.messages[0].code).isEqualTo(ValidationCode.SEC236A_SENTENCE_TYPE_INCORRECT)
  }

  @Test
  fun `Test SOPC sentences should have license term of exactly 1 year`() {
    val sentences = listOf(
      validSopcSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(1, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE)
        ),
      ),
      validSopcSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(0, 12, 0, 0, SentenceTerms.LICENCE_TERM_CODE)
        ),
      ),
      validSopcSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(1, 0, 0, 1, SentenceTerms.LICENCE_TERM_CODE)
        ),
      ),
      validSopcSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(0, 11, 3, 0, SentenceTerms.LICENCE_TERM_CODE)
        ),
      )
    )
    val result = validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, null))

    assertThat(result.type).isEqualTo(ValidationType.VALIDATION)
    assertThat(result.messages).hasSize(2)
    assertThat(result.messages[0].code).isEqualTo(ValidationCode.SOPC_LICENCE_TERM_NOT_12_MONTHS)
    assertThat(result.messages[1].code).isEqualTo(ValidationCode.SOPC_LICENCE_TERM_NOT_12_MONTHS)
  }

  private companion object {
    val FIRST_MAY_2018: LocalDate = LocalDate.of(2018, 5, 1)
    val FIRST_MAY_2020: LocalDate = LocalDate.of(2020, 5, 1)
    val FIRST_MAY_2021: LocalDate = LocalDate.of(2021, 5, 1)
  }
}
