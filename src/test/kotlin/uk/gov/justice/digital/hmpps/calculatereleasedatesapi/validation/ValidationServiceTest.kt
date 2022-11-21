package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.LAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.SPECIAL_REMISSION
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SentencesExtractionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_RADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_UAL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_CONSECUTIVE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_CONSECUTIVE_TO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_MISSING_FINE_AMOUNT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_WITH_PAYMENTS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.LASPO_AR_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.MORE_THAN_ONE_IMPRISONMENT_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.MORE_THAN_ONE_LICENCE_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SEC236A_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SENTENCE_HAS_NO_LICENCE_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SOPC_LICENCE_TERM_NOT_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_SENTENCE_TYPE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ZERO_IMPRISONMENT_TERM
import java.math.BigDecimal
import java.time.LocalDate

class ValidationServiceTest {

  private val validationService = ValidationService(FeatureToggles(true), SentencesExtractionService())
  private val validSdsSentence = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 7,
    lineSequence = LINE_SEQ,
    caseSequence = CASE_SEQ,
    sentenceDate = FIRST_MAY_2018,
    terms = listOf(
      SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE)
    ),
    sentenceCalculationType = SentenceCalculationType.ADIMP.name,
    sentenceCategory = "2003",
    sentenceStatus = "a",
    sentenceTypeDescription = "This is a sentence type",
    offences = listOf(),
  )
  private val validSopcSentence = validSdsSentence.copy(
    terms = listOf(
      SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      SentenceTerms(1, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
    ),
    sentenceCalculationType = SentenceCalculationType.SOPC21.name,
    sentenceDate = FIRST_MAY_2021
  )
  private val validEdsSentence = validSdsSentence.copy(
    sentenceDate = FIRST_MAY_2021,
    terms = listOf(
      SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      SentenceTerms(2, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
    ),
    sentenceCalculationType = SentenceCalculationType.LASPO_DR.name,
  )
  private val validAFineSentence = validSdsSentence.copy(
    terms = listOf(
      SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE)
    ),
    sentenceCalculationType = SentenceCalculationType.AFINE.name,
    sentenceDate = FIRST_MAY_2021,
    fineAmount = BigDecimal("100")
  )
  private val validPrisoner = PrisonerDetails(offenderNo = "", bookingId = 1, dateOfBirth = LocalDate.of(1, 2, 3))
  private val validAdjustments = BookingAndSentenceAdjustments(emptyList(), emptyList())
  private val lawfullyAtLargeBookingAdjustment = BookingAndSentenceAdjustments(
    listOf(
      BookingAdjustment(
        active = true,
        fromDate = LocalDate.of(2020, 1, 1),
        numberOfDays = 2,
        type = LAWFULLY_AT_LARGE
      )
    ),
    emptyList()
  )
  private val specialRemissionBookingAdjustment = BookingAndSentenceAdjustments(
    listOf(
      BookingAdjustment(
        active = true,
        fromDate = LocalDate.of(2020, 1, 1),
        numberOfDays = 2,
        type = SPECIAL_REMISSION
      )
    ),
    emptyList()
  )

  @Test
  fun `Test EDS valid sentence should pass`() {
    val result = validationService.validate(
      PrisonApiSourceData(
        listOf(validEdsSentence), validPrisoner, validAdjustments, listOf(),
        null
      )
    )

    assertThat(result).hasSize(0)
  }

  @Test
  fun `Test EDS sentences should have imprisonment term`() {
    val sentence = validEdsSentence.copy(
      terms = listOf(
        SentenceTerms(2, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
      ),
    )
    val result =
      validationService.validate(PrisonApiSourceData(listOf(sentence), validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(
          SENTENCE_HAS_NO_IMPRISONMENT_TERM,
          listOf(CASE_SEQ.toString(), LINE_SEQ.toString())
        )
      )
    )
  }

  @Test
  fun `Test EDS sentences should have imprisonment term with some duration`() {
    val sentence = validEdsSentence.copy(
      terms = listOf(
        SentenceTerms(0, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
        SentenceTerms(2, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
      ),
    )
    val result =
      validationService.validate(PrisonApiSourceData(listOf(sentence), validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(
          ZERO_IMPRISONMENT_TERM,
          listOf(CASE_SEQ.toString(), LINE_SEQ.toString())
        )
      )
    )
  }

  @Test
  fun `Test EDS sentences should have license term`() {
    val sentence = validEdsSentence.copy(
      terms = listOf(
        SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE)
      ),
    )
    val result =
      validationService.validate(PrisonApiSourceData(listOf(sentence), validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(
          SENTENCE_HAS_NO_LICENCE_TERM,
          listOf(CASE_SEQ.toString(), LINE_SEQ.toString())
        )
      )
    )
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
    val result =
      validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR, arguments = listOf("1", "2")),
        ValidationMessage(EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR, arguments = listOf("1", "2"))
      )
    )
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
    val result =
      validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEmpty()
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
    val result =
      validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).hasSize(1)
    assertThat(result[0].code).isEqualTo(EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS)

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      )
    )
  }

  @Test
  fun `Test EDS sentences should be correctly dated`() {
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
    val result =
      validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
        ValidationMessage(EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
        ValidationMessage(EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      )
    )
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
    val result =
      validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(LASPO_AR_SENTENCE_TYPE_INCORRECT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString()))
      )
    )
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
    val result =
      validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(MORE_THAN_ONE_IMPRISONMENT_TERM, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
        ValidationMessage(MORE_THAN_ONE_LICENCE_TERM, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      )
    )
  }

  @Test
  fun `Test SOPC valid sentence should pass`() {
    val result = validationService.validate(
      PrisonApiSourceData(
        listOf(validSopcSentence),
        validPrisoner,
        validAdjustments,
        listOf(),
        null
      )
    )

    assertThat(result).hasSize(0)
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
    val result =
      validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
        ValidationMessage(SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      )
    )
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
    val result =
      validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(SEC236A_SENTENCE_TYPE_INCORRECT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      )
    )
  }

  @Test
  fun `Validate future dated adjustments`() {
    val result = validationService.validate(
      PrisonApiSourceData(
        listOf(validEdsSentence), validPrisoner,
        BookingAndSentenceAdjustments(
          listOf(
            BookingAdjustment(
              active = true,
              fromDate = LocalDate.now().plusDays(1),
              type = BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED,
              numberOfDays = 5
            ),
            BookingAdjustment(
              active = true,
              fromDate = LocalDate.now().plusDays(1),
              type = BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED,
              numberOfDays = 5
            ),
            BookingAdjustment(
              active = true,
              fromDate = LocalDate.now().plusDays(1),
              type = BookingAdjustmentType.UNLAWFULLY_AT_LARGE,
              numberOfDays = 5
            ),
          ),
          listOf()
        ),
        listOf(),
        null
      )
    )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(code = ADJUSTMENT_FUTURE_DATED_ADA),
        ValidationMessage(code = ADJUSTMENT_FUTURE_DATED_RADA),
        ValidationMessage(code = ADJUSTMENT_FUTURE_DATED_UAL)
      )
    )
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
    val result =
      validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(SOPC_LICENCE_TERM_NOT_12_MONTHS, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
        ValidationMessage(SOPC_LICENCE_TERM_NOT_12_MONTHS, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      )
    )
  }

  @Test
  fun `Test SDS sentence is valid`() {
    val sentences = listOf(validSdsSentence)
    val result =
      validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEmpty()
  }

  @Test
  fun `Test A FINE sentence is valid`() {
    val sentences = listOf(validAFineSentence)
    val result =
      validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEmpty()
  }

  @Test
  fun `Test A FINE sentence with payments is unsupported`() {
    val sentences = listOf(validAFineSentence)
    val result = validationService.validate(
      PrisonApiSourceData(
        sentences, validPrisoner, validAdjustments,
        listOf(OffenderFinePayment(1, LocalDate.now(), BigDecimal.ONE)), null
      )
    )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(A_FINE_SENTENCE_WITH_PAYMENTS),
      )
    )
  }

  @Test
  fun `Test A FINE sentence consecutive to unsupported`() {
    val sentences = listOf(
      validSdsSentence.copy(
        sentenceSequence = 1
      ),
      validAFineSentence.copy(
        sentenceSequence = 2,
        consecutiveToSequence = 1
      )
    )
    val result =
      validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(A_FINE_SENTENCE_CONSECUTIVE_TO),
      )
    )
  }

  @Test
  fun `Test A FINE sentence consecutive from unsupported`() {
    val sentences = listOf(
      validAFineSentence.copy(
        sentenceSequence = 1
      ),
      validSdsSentence.copy(
        sentenceSequence = 2,
        consecutiveToSequence = 1
      )
    )
    val result =
      validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(A_FINE_SENTENCE_CONSECUTIVE),
      )
    )
  }

  @Test
  fun `Test A FINE sentence multiple unsupported`() {
    val sentences = listOf(
      validAFineSentence.copy(
        sentenceSequence = 1
      ),
      validSdsSentence.copy(
        sentenceSequence = 2,
        consecutiveToSequence = 1
      )
    )
    val result = validationService.validate(
      PrisonApiSourceData(
        sentences,
        validPrisoner,
        validAdjustments,
        listOf(OffenderFinePayment(1, LocalDate.now(), BigDecimal.ONE)),
        null
      )
    )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(A_FINE_SENTENCE_WITH_PAYMENTS),
        ValidationMessage(A_FINE_SENTENCE_CONSECUTIVE),
      )
    )
  }

  @Test
  fun `Test A FINE unsupported with feature toggle off`() {
    val sentences = listOf(validAFineSentence)
    val result = ValidationService(FeatureToggles(false), SentencesExtractionService()).validate(
      PrisonApiSourceData(
        sentences,
        validPrisoner,
        validAdjustments,
        listOf(),
        null
      )
    )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(UNSUPPORTED_SENTENCE_TYPE, listOf("2003", "This is a sentence type")),
      )
    )
  }

  @Test
  fun `Test A FINE invalid without fine amount`() {
    val sentences = listOf(
      validAFineSentence.copy(
        fineAmount = null
      )
    )
    val result =
      validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(A_FINE_SENTENCE_MISSING_FINE_AMOUNT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      )
    )
  }

  @Test
  fun `Test SDS sentence unsupported category 1991`() {
    val sentences = listOf(
      validSdsSentence.copy(
        sentenceCategory = "1991"
      )
    )
    val result =
      validationService.validate(PrisonApiSourceData(sentences, validPrisoner, validAdjustments, listOf(), null))

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(UNSUPPORTED_SENTENCE_TYPE, listOf("1991", "This is a sentence type")),
      )
    )
  }

  @Test
  fun `Test Lawfully at Large adjustments at a booking level cause validation errors`() {
    val result = validationService.validate(
      PrisonApiSourceData(
        sentenceAndOffences = listOf(validSdsSentence),
        prisonerDetails = validPrisoner,
        bookingAndSentenceAdjustments = lawfullyAtLargeBookingAdjustment,
        returnToCustodyDate = null,
      )
    )

    assertThat(result).isEqualTo(
      listOf(ValidationMessage(UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE))
    )
  }

  @Test
  fun `Test Special Remission adjustments at a booking level cause validation errors`() {
    val result = validationService.validate(
      PrisonApiSourceData(
        sentenceAndOffences = listOf(validSdsSentence),
        prisonerDetails = validPrisoner,
        bookingAndSentenceAdjustments = specialRemissionBookingAdjustment,
        returnToCustodyDate = null,
      )
    )

    assertThat(result).isEqualTo(
      listOf(ValidationMessage(UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION))
    )
  }

  private companion object {
    val FIRST_MAY_2018: LocalDate = LocalDate.of(2018, 5, 1)
    val FIRST_MAY_2021: LocalDate = LocalDate.of(2021, 5, 1)
    private const val LINE_SEQ = 2
    private const val CASE_SEQ = 1
  }
}
