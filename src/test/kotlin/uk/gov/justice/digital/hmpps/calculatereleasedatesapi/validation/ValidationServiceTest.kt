package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.context.annotation.Profile
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.STANDARD_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.LAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.SPECIAL_REMISSION
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.FTR
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.FTR_14_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SentencesExtractionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.TrancheOne
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.BookingHelperTest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_RADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_UAL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_CONSECUTIVE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_CONSECUTIVE_TO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_MISSING_FINE_AMOUNT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_WITH_PAYMENTS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.BOTUS_CONSECUTIVE_OR_CONCURRENT_TO_OTHER_SENTENCE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.DTO_CONSECUTIVE_TO_SENTENCE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.DTO_HAS_SENTENCE_CONSECUTIVE_TO_IT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_14_DAYS_AGGREGATE_GE_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_14_DAYS_SENTENCE_GE_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_28_DAYS_AGGREGATE_LT_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_28_DAYS_SENTENCE_LT_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_SENTENCES_CONFLICT_WITH_EACH_OTHER
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_TYPE_14_DAYS_AGGREGATE_GE_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_TYPE_14_DAYS_BUT_LENGTH_IS_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_TYPE_14_DAYS_SENTENCE_GE_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_TYPE_28_DAYS_AGGREGATE_LT_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_TYPE_28_DAYS_BUT_LENGTH_IS_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_TYPE_28_DAYS_SENTENCE_LT_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.LASPO_AR_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.MORE_THAN_ONE_IMPRISONMENT_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.MORE_THAN_ONE_LICENCE_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.PRE_PCSC_DTO_WITH_ADJUSTMENT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SEC236A_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SENTENCE_HAS_NO_IMPRISONMENT_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SENTENCE_HAS_NO_LICENCE_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SOPC_LICENCE_TERM_NOT_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_SDS40_RECALL_SENTENCE_TYPE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_SENTENCE_TYPE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ZERO_IMPRISONMENT_TERM
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.*

@Profile("tests")
class ValidationServiceTest {
  private var validationService = ValidationService(SentencesExtractionService(), FeatureToggles(botus = true), SDS40_TRANCHE_ONE)
  private val validSdsSentence = NormalisedSentenceAndOffence(
    bookingId = 1L,
    sentenceSequence = 7,
    lineSequence = LINE_SEQ,
    caseSequence = CASE_SEQ,
    sentenceDate = FIRST_MAY_2018,
    terms = listOf(
      SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
    ),
    sentenceCalculationType = SentenceCalculationType.ADIMP.name,
    sentenceCategory = "2003",
    sentenceStatus = "a",
    sentenceTypeDescription = "This is a sentence type",
    offence = OffenderOffence(
      1,
      LocalDate.of(2015, 4, 1),
      null,
      "A123456",
      "TEST OFFENCE 2",
    ),
    caseReference = null,
    fineAmount = null,
    courtDescription = null,
    consecutiveToSequence = null,
  )
  private val sentenceWithMissingOffenceDates = NormalisedSentenceAndOffence(
    bookingId = 1L,
    sentenceSequence = 7,
    lineSequence = LINE_SEQ,
    caseSequence = CASE_SEQ,
    sentenceDate = FIRST_MAY_2018,
    terms = listOf(
      SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
    ),
    sentenceCalculationType = SentenceCalculationType.ADIMP.name,
    sentenceCategory = "2003",
    sentenceStatus = "a",
    sentenceTypeDescription = "This is a sentence type",
    offence = OffenderOffence(
      offenderChargeId = 1L,
      offenceStartDate = null,
      offenceEndDate = null,
      offenceCode = "Dummy Offence",
      offenceDescription = "A Dummy description",
    ),
    caseReference = null,
    fineAmount = null,
    courtDescription = null,
    consecutiveToSequence = null,
  )

  private val validSopcSentence = validSdsSentence.copy(
    terms = listOf(
      SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      SentenceTerms(1, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
    ),
    sentenceCalculationType = SentenceCalculationType.SOPC21.name,
    sentenceDate = FIRST_MAY_2021,
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
      SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
    ),
    sentenceCalculationType = SentenceCalculationType.AFINE.name,
    sentenceDate = FIRST_MAY_2021,
    fineAmount = BigDecimal("100"),
  )
  private val validEdsRecallSentence = validEdsSentence.copy(
    sentenceCalculationType = SentenceCalculationType.LR_LASPO_DR.name,
  )
  private val validSopcRecallSentence = validSopcSentence.copy(
    sentenceCalculationType = SentenceCalculationType.LR_SOPC21.name,
  )
  private val lawfullyAtLargeBookingAdjustment = BookingAndSentenceAdjustments(
    listOf(
      BookingAdjustment(
        active = true,
        fromDate = LocalDate.of(2020, 1, 1),
        numberOfDays = 2,
        type = LAWFULLY_AT_LARGE,
      ),
    ),
    emptyList(),
  )
  private val specialRemissionBookingAdjustment = BookingAndSentenceAdjustments(
    listOf(
      BookingAdjustment(
        active = true,
        fromDate = LocalDate.of(2020, 1, 1),
        numberOfDays = 2,
        type = SPECIAL_REMISSION,
      ),
    ),
    emptyList(),
  )

  @ParameterizedTest
  @ValueSource(strings = ["SC07002", "SC07003", "SC07004", "SC07005", "SC07006", "SC07007", "SC07008", "SC07009", "SC07010", "SC07011", "SC07012", "SC07013"])
  fun `Test Sentences with unsupported offenceCodes SC07002 to SC07013 returns validation message`(offenceCode: String) {
    // Arrange
    val invalidSentence = validSdsSentence.copy(
      offence = validSdsSentence.offence.copy(offenceCode = offenceCode),
    )

    // Act
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(listOf(SentenceAndOffenceWithReleaseArrangements(invalidSentence, false, SDSEarlyReleaseExclusionType.NO)), VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    // Assert
    assertThat(result).isNotEmpty
    assertThat(result[0].code).isEqualTo(ValidationCode.UNSUPPORTED_OFFENCE_ENCOURAGING_OR_ASSISTING)
  }

  @ParameterizedTest
  @ValueSource(strings = ["SC07001", "SC07014", "FG06019", "TH68058"])
  fun `Test Sentences with supported offenceCodes shouldn't return validation message`(offenceCode: String) {
    // Arrange
    val validSentence = validSdsSentence.copy(
      offence = validSdsSentence.offence.copy(offenceCode = offenceCode),
    )

    // Act
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(listOf(SentenceAndOffenceWithReleaseArrangements(validSentence, false, SDSEarlyReleaseExclusionType.NO)), VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    // Assert
    assertThat(result).isEmpty()
  }

  @Test
  fun `Test EDS valid sentence should pass`() {
    val result = validationService.validateBeforeCalculation(
      PrisonApiSourceData(
        listOf(SentenceAndOffenceWithReleaseArrangements(validEdsSentence, false, SDSEarlyReleaseExclusionType.NO)),
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(),
        null,
      ),
      USER_INPUTS,
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
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(listOf(SentenceAndOffenceWithReleaseArrangements(sentence, false, SDSEarlyReleaseExclusionType.NO)), VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(
          SENTENCE_HAS_NO_IMPRISONMENT_TERM,
          listOf(CASE_SEQ.toString(), LINE_SEQ.toString()),
        ),
      ),
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
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(listOf(SentenceAndOffenceWithReleaseArrangements(sentence, false, SDSEarlyReleaseExclusionType.NO)), VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(
          ZERO_IMPRISONMENT_TERM,
          listOf(CASE_SEQ.toString(), LINE_SEQ.toString()),
        ),
      ),
    )
  }

  @Test
  fun `Test EDS sentences should have license term`() {
    val sentence = validEdsSentence.copy(
      terms = listOf(
        SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(listOf(SentenceAndOffenceWithReleaseArrangements(sentence, false, SDSEarlyReleaseExclusionType.NO)), VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(
          SENTENCE_HAS_NO_LICENCE_TERM,
          listOf(CASE_SEQ.toString(), LINE_SEQ.toString()),
        ),
      ),
    )
  }

  @Test
  fun `Test EDS sentences should have license term of at least 1 year`() {
    val sentences = listOf(
      validEdsSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(0, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
        ),
      ),
      validEdsSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(0, 11, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
        ),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR, arguments = listOf("1", "2")),
        ValidationMessage(EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR, arguments = listOf("1", "2")),
      ),
    )
  }

  @Test
  fun `Test EDS sentences should have license term of at least 1 year valid`() {
    val sentences = listOf(
      validEdsSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(0, 11, 5, 0, SentenceTerms.LICENCE_TERM_CODE),
        ),
      ),
      validEdsSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(0, 0, 0, 377, SentenceTerms.LICENCE_TERM_CODE),
        ),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEmpty()
  }

  @Test
  fun `Test EDS sentences should have license term of at less than 8 years`() {
    val sentences = listOf(
      validEdsSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(8, 0, 0, 1, SentenceTerms.LICENCE_TERM_CODE),
        ),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).hasSize(1)
    assertThat(result[0].code).isEqualTo(EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS)

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      ),
    )
  }

  @Test
  fun `Test EDS sentences should be correctly dated`() {
    val sentences = listOf(
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.EDS18.name,
        sentenceDate = ImportantDates.EDS18_SENTENCE_TYPES_START_DATE.minusDays(1),
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.EDSU18.name,
        sentenceDate = ImportantDates.EDS18_SENTENCE_TYPES_START_DATE.minusDays(1),
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.EDS21.name,
        sentenceDate = ImportantDates.EDS18_SENTENCE_TYPES_START_DATE.minusDays(1),
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.LASPO_DR.name,
        sentenceDate = ImportantDates.EDS18_SENTENCE_TYPES_START_DATE.minusDays(1),
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.EDS18.name,
        sentenceDate = ImportantDates.EDS18_SENTENCE_TYPES_START_DATE.plusDays(1),
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.EDSU18.name,
        sentenceDate = ImportantDates.EDS18_SENTENCE_TYPES_START_DATE.plusDays(1),
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.EDS21.name,
        sentenceDate = ImportantDates.EDS18_SENTENCE_TYPES_START_DATE.plusDays(1),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
        ValidationMessage(EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
        ValidationMessage(EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      ),
    )
  }

  @Test
  fun `Test LASPO_AR sentences should be correctly dated`() {
    val sentences = listOf(
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.LASPO_AR.name,
        sentenceDate = ImportantDates.LASPO_AR_SENTENCE_TYPES_END_DATE.plusDays(1),
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.LASPO_DR.name,
        sentenceDate = ImportantDates.LASPO_AR_SENTENCE_TYPES_END_DATE.minusDays(1),
      ),
      validEdsSentence.copy(
        sentenceCalculationType = SentenceCalculationType.LASPO_AR.name,
        sentenceDate = ImportantDates.LASPO_AR_SENTENCE_TYPES_END_DATE.minusDays(1),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(LASPO_AR_SENTENCE_TYPE_INCORRECT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      ),
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
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(MORE_THAN_ONE_IMPRISONMENT_TERM, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
        ValidationMessage(MORE_THAN_ONE_LICENCE_TERM, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      ),
    )
  }

  @Test
  fun `Test SOPC valid sentence should pass`() {
    val result = validationService.validateBeforeCalculation(
      PrisonApiSourceData(
        listOf(validSopcSentence).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(),
        null,
      ),
      USER_INPUTS,
    )

    assertThat(result).hasSize(0)
  }

  @Test
  fun `Test SOPC18 SOPC21 sentences should be correctly dated`() {
    val sentences = listOf(
      validSopcSentence.copy(
        sentenceCalculationType = SentenceCalculationType.SOPC18.name,
        sentenceDate = ImportantDates.SEC_91_END_DATE.minusDays(1),
      ),
      validSopcSentence.copy(
        sentenceCalculationType = SentenceCalculationType.SOPC18.name,
        sentenceDate = ImportantDates.SEC_91_END_DATE,
      ),
      validSopcSentence.copy(
        sentenceCalculationType = SentenceCalculationType.SOPC21.name,
        sentenceDate = ImportantDates.SEC_91_END_DATE.minusDays(1),
      ),
      validSopcSentence.copy(
        sentenceCalculationType = SentenceCalculationType.SOPC21.name,
        sentenceDate = ImportantDates.SEC_91_END_DATE,
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
        ValidationMessage(SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      ),
    )
  }

  @Test
  fun `Test SEC236A sentences should be correctly dated`() {
    val sentences = listOf(
      validSopcSentence.copy(
        sentenceCalculationType = SentenceCalculationType.SEC236A.name,
        sentenceDate = ImportantDates.SEC_91_END_DATE,
      ),
      validSopcSentence.copy(
        sentenceCalculationType = SentenceCalculationType.SEC236A.name,
        sentenceDate = ImportantDates.SEC_91_END_DATE.minusDays(1),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(SEC236A_SENTENCE_TYPE_INCORRECT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      ),
    )
  }

  @Test
  fun `Validate future dated adjustments`() {
    val result = validationService.validateBeforeCalculation(
      PrisonApiSourceData(
        listOf(validEdsSentence).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
        VALID_PRISONER,
        BookingAndSentenceAdjustments(
          listOf(
            BookingAdjustment(
              active = true,
              fromDate = LocalDate.now().plusDays(1),
              type = BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED,
              numberOfDays = 5,
            ),
            BookingAdjustment(
              active = true,
              fromDate = LocalDate.now().plusDays(1),
              type = BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED,
              numberOfDays = 5,
            ),
            BookingAdjustment(
              active = true,
              fromDate = LocalDate.now().plusDays(1),
              type = BookingAdjustmentType.UNLAWFULLY_AT_LARGE,
              numberOfDays = 5,
            ),
          ),
          listOf(),
        ),
        listOf(),
        null,
      ),
      USER_INPUTS,
    )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(code = ADJUSTMENT_FUTURE_DATED_ADA),
        ValidationMessage(code = ADJUSTMENT_FUTURE_DATED_RADA),
        ValidationMessage(code = ADJUSTMENT_FUTURE_DATED_UAL),
      ),
    )
  }

  @Test
  fun `Test SOPC sentences should have license term of exactly 1 year`() {
    val sentences = listOf(
      validSopcSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(1, 0, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
        ),
      ),
      validSopcSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(0, 12, 0, 0, SentenceTerms.LICENCE_TERM_CODE),
        ),
      ),
      validSopcSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(1, 0, 0, 1, SentenceTerms.LICENCE_TERM_CODE),
        ),
      ),
      validSopcSentence.copy(
        terms = listOf(
          SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          SentenceTerms(0, 11, 3, 0, SentenceTerms.LICENCE_TERM_CODE),
        ),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(SOPC_LICENCE_TERM_NOT_12_MONTHS, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
        ValidationMessage(SOPC_LICENCE_TERM_NOT_12_MONTHS, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      ),
    )
  }

  @Test
  fun `Test SDS sentence is valid`() {
    val sentences = listOf(SentenceAndOffenceWithReleaseArrangements(validSdsSentence, false, SDSEarlyReleaseExclusionType.NO))
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEmpty()
  }

  @Test
  fun `Test A FINE sentence is valid`() {
    val sentences = listOf(validAFineSentence)
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEmpty()
  }

  @Test
  fun `Test A FINE sentence with payments is unsupported`() {
    val sentences = listOf(validAFineSentence)
    val result = validationService.validateBeforeCalculation(
      PrisonApiSourceData(
        sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(OffenderFinePayment(1, LocalDate.now(), BigDecimal.ONE)),
        null,
      ),
      USER_INPUTS,
    )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(A_FINE_SENTENCE_WITH_PAYMENTS),
      ),
    )
  }

  @Test
  fun `Test A DTO sentence consecutive to unsupported`() {
    val sentences = listOf(
      validSdsSentence.copy(
        sentenceSequence = 1,
      ),
      validSdsSentence.copy(
        sentenceSequence = 2,
        consecutiveToSequence = 1,
        sentenceCalculationType = "DTO",
        terms = listOf(SentenceTerms(years = 1, code = "IMP")),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(DTO_CONSECUTIVE_TO_SENTENCE),
      ),
    )
  }

  @Test
  fun `Test A Botus sentence consecutive to another sentenceType is unsupported`() {
    val sentences = listOf(
      validSdsSentence.copy(
        sentenceSequence = 1,
      ),
      validSdsSentence.copy(
        sentenceSequence = 2,
        consecutiveToSequence = 1,
        sentenceCalculationType = "BOTUS",
        terms = listOf(SentenceTerms(days = 7, code = "IMP")),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(BOTUS_CONSECUTIVE_OR_CONCURRENT_TO_OTHER_SENTENCE),
      ),
    )
  }

  @Test
  fun `Test Two Botus sentence consecutive are supported`() {
    val sentences = listOf(
      validSdsSentence.copy(
        sentenceSequence = 1,
        sentenceCalculationType = "BOTUS",
        terms = listOf(SentenceTerms(days = 7, code = "IMP")),
      ),
      validSdsSentence.copy(
        sentenceSequence = 2,
        consecutiveToSequence = 1,
        sentenceCalculationType = "BOTUS",
        terms = listOf(SentenceTerms(days = 7, code = "IMP")),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEmpty()
  }

  @Test
  fun `Test BOTUS feature toggle results in unsupported sentence type if disabled`() {
    val validationService = ValidationService(SentencesExtractionService(), FeatureToggles(botus = false), SDS40_TRANCHE_ONE)
    val sentenceAndOffences = validSdsSentence.copy(
      sentenceCalculationType = SentenceCalculationType.BOTUS.name,
      terms = listOf(
        SentenceTerms(0, 0, 0, 10, SentenceTerms.LICENCE_TERM_CODE),
      ),
    )
    val result = validationService.validateBeforeCalculation(
      PrisonApiSourceData(
        sentenceAndOffences = listOf(sentenceAndOffences).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
        prisonerDetails = VALID_PRISONER,
        bookingAndSentenceAdjustments = BookingAndSentenceAdjustments(emptyList(), emptyList()),
        returnToCustodyDate = null,
      ),
      USER_INPUTS,
    )
    assertThat(result).containsExactly(ValidationMessage(UNSUPPORTED_SENTENCE_TYPE, listOf("2003", "This is a sentence type")))
  }

  @Test
  fun `Test A DTO sentence consecutive from unsupported`() {
    val sentences = listOf(
      validSdsSentence.copy(
        sentenceSequence = 1,
        sentenceCalculationType = "DTO",
        terms = listOf(SentenceTerms(years = 1, code = "IMP")),
      ),
      validSdsSentence.copy(
        sentenceSequence = 2,
        consecutiveToSequence = 1,
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(DTO_HAS_SENTENCE_CONSECUTIVE_TO_IT),
      ),
    )
  }

  @Test
  fun `Test multiple DTO sentence consecutive to unsupported`() {
    val sentences = listOf(
      validSdsSentence.copy(
        sentenceSequence = 1,
        sentenceCalculationType = "DTO",
        terms = listOf(SentenceTerms(years = 1, code = "IMP")),
      ),
      validSdsSentence.copy(
        sentenceSequence = 2,
        consecutiveToSequence = 1,

      ),
      validSdsSentence.copy(
        sentenceSequence = 3,
        consecutiveToSequence = 2,
        sentenceCalculationType = "DTO",
        terms = listOf(SentenceTerms(years = 1, code = "IMP")),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(DTO_HAS_SENTENCE_CONSECUTIVE_TO_IT),
        ValidationMessage(DTO_CONSECUTIVE_TO_SENTENCE),
      ),
    )
  }

  @Test
  fun `Test multiple DTO sentence consecutive no error`() {
    val sentences = listOf(
      validSdsSentence.copy(
        sentenceSequence = 1,
        sentenceCalculationType = "DTO",
        terms = listOf(SentenceTerms(years = 1, code = "IMP")),
      ),
      validSdsSentence.copy(
        sentenceSequence = 2,
        consecutiveToSequence = 1,
        sentenceCalculationType = "DTO",
        terms = listOf(SentenceTerms(years = 1, code = "IMP")),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEmpty()
  }

  @Test
  fun `Test DTO pre pcsc with remand associated`() {
    val sentences = listOf(
      validSdsSentence.copy(
        sentenceSequence = 1,
        sentenceCalculationType = "DTO",
        sentenceDate = LocalDate.of(2021, 2, 1),
        terms = listOf(SentenceTerms(years = 1, code = "IMP")),
      ),
      validSdsSentence.copy(
        sentenceSequence = 2,
        consecutiveToSequence = 1,
        sentenceCalculationType = "DTO",
        terms = listOf(SentenceTerms(years = 1, code = "IMP")),
      ),
    )
    val adjustments = BookingAndSentenceAdjustments(
      bookingAdjustments = emptyList(),
      sentenceAdjustments = listOf(
        SentenceAdjustment(
          sentenceSequence = 1,
          active = true,
          fromDate = LocalDate.of(2021, 1, 30),
          toDate = LocalDate.of(2021, 1, 31),
          numberOfDays = 1,
          type = SentenceAdjustmentType.REMAND,
        ),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, adjustments, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(PRE_PCSC_DTO_WITH_ADJUSTMENT, listOf("remand")),
      ),
    )
  }

  @Test
  fun `Test DTO pre pcsc with remand and tagged bail associated`() {
    val sentences = listOf(
      validSdsSentence.copy(
        sentenceSequence = 1,
        sentenceCalculationType = "DTO",
        sentenceDate = LocalDate.of(2021, 2, 1),
        terms = listOf(SentenceTerms(years = 1, code = "IMP")),
      ),
      validSdsSentence.copy(
        sentenceSequence = 2,
        consecutiveToSequence = 1,
        sentenceCalculationType = "DTO",
        terms = listOf(SentenceTerms(years = 1, code = "IMP")),
      ),
    )
    val adjustments = BookingAndSentenceAdjustments(
      bookingAdjustments = emptyList(),
      sentenceAdjustments = listOf(
        SentenceAdjustment(
          sentenceSequence = 1,
          active = true,
          fromDate = LocalDate.of(2021, 1, 30),
          toDate = LocalDate.of(2021, 1, 31),
          numberOfDays = 1,
          type = SentenceAdjustmentType.REMAND,
        ),
        SentenceAdjustment(
          sentenceSequence = 1,
          active = true,
          fromDate = LocalDate.of(2021, 1, 30),
          toDate = LocalDate.of(2021, 1, 31),
          numberOfDays = 1,
          type = SentenceAdjustmentType.TAGGED_BAIL,
        ),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, adjustments, listOf(), null),
        USER_INPUTS,
      )
    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(PRE_PCSC_DTO_WITH_ADJUSTMENT, listOf("remand and tagged bail")),
      ),
    )
  }

  @Test
  fun `Test A FINE sentence consecutive to unsupported`() {
    val sentences = listOf(
      validSdsSentence.copy(
        sentenceSequence = 1,
      ),
      validAFineSentence.copy(
        sentenceSequence = 2,
        consecutiveToSequence = 1,
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(A_FINE_SENTENCE_CONSECUTIVE_TO),
      ),
    )
  }

  @Test
  fun `Test A FINE sentence consecutive from unsupported`() {
    val sentences = listOf(
      validAFineSentence.copy(
        sentenceSequence = 1,
      ),
      validSdsSentence.copy(
        sentenceSequence = 2,
        consecutiveToSequence = 1,
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(A_FINE_SENTENCE_CONSECUTIVE),
      ),
    )
  }

  @Test
  fun `Test A FINE sentence multiple unsupported`() {
    val sentences = listOf(
      validAFineSentence.copy(
        sentenceSequence = 1,
      ),
      validSdsSentence.copy(
        sentenceSequence = 2,
        consecutiveToSequence = 1,
      ),
    )
    val result = validationService.validateBeforeCalculation(
      PrisonApiSourceData(
        sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(OffenderFinePayment(1, LocalDate.now(), BigDecimal.ONE)),
        null,
      ),
      USER_INPUTS,
    )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(A_FINE_SENTENCE_WITH_PAYMENTS),
        ValidationMessage(A_FINE_SENTENCE_CONSECUTIVE),
      ),
    )
  }

  @Test
  fun `Test A FINE invalid without fine amount`() {
    val sentences = listOf(
      validAFineSentence.copy(
        fineAmount = null,
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(A_FINE_SENTENCE_MISSING_FINE_AMOUNT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      ),
    )
  }

  @Test
  fun `Test SDS sentence unsupported category 1991`() {
    val sentences = listOf(
      validSdsSentence.copy(
        sentenceCategory = "1991",
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        PrisonApiSourceData(sentences.map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }, VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
        CalculationUserInputs(),
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(UNSUPPORTED_SENTENCE_TYPE, listOf("1991", "This is a sentence type")),
      ),
    )
  }

  @Test
  fun `Test Lawfully at Large adjustments at a booking level cause validation errors`() {
    val result = validationService.validateBeforeCalculation(
      PrisonApiSourceData(
        sentenceAndOffences = listOf(SentenceAndOffenceWithReleaseArrangements(validSdsSentence, false, SDSEarlyReleaseExclusionType.NO)),
        prisonerDetails = VALID_PRISONER,
        bookingAndSentenceAdjustments = lawfullyAtLargeBookingAdjustment,
        returnToCustodyDate = null,
      ),
      USER_INPUTS,
    )

    assertThat(result).isEqualTo(
      listOf(ValidationMessage(UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE)),
    )
  }

  @Test
  fun `Test Special Remission adjustments at a booking level cause validation errors`() {
    val result = validationService.validateBeforeCalculation(
      PrisonApiSourceData(
        sentenceAndOffences = listOf(SentenceAndOffenceWithReleaseArrangements(validSdsSentence, false, SDSEarlyReleaseExclusionType.NO)),
        prisonerDetails = VALID_PRISONER,
        bookingAndSentenceAdjustments = specialRemissionBookingAdjustment,
        returnToCustodyDate = null,
      ),
      CalculationUserInputs(),
    )

    assertThat(result).isEqualTo(
      listOf(ValidationMessage(UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION)),
    )
  }

  @Test
  fun `Test EDS recalls supported`() {
    val result = validationService.validateBeforeCalculation(
      PrisonApiSourceData(listOf(SentenceAndOffenceWithReleaseArrangements(validEdsRecallSentence, false, SDSEarlyReleaseExclusionType.NO)), VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
      CalculationUserInputs(),
    )

    assertThat(result).isEmpty()
  }

  @Test
  fun `Test SOPC recalls supported`() {
    val result = validationService.validateBeforeCalculation(
      PrisonApiSourceData(listOf(SentenceAndOffenceWithReleaseArrangements(validSopcRecallSentence, false, SDSEarlyReleaseExclusionType.NO)), VALID_PRISONER, VALID_ADJUSTMENTS, listOf(), null),
      CalculationUserInputs(),
    )

    assertThat(result).isEmpty()
  }

  @Test
  fun `Test FTR validation precalc`() {
    val result = validationService.validateBeforeCalculation(
      VALID_FTR_SOURCE_DATA.copy(sentenceAndOffences = listOf(FTR_14_DAY_SENTENCE, FTR_28_DAY_SENTENCE).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }),
      USER_INPUTS,
    )

    assertThat(result).isEqualTo(listOf(ValidationMessage(FTR_SENTENCES_CONFLICT_WITH_EACH_OTHER)))
  }

  @Test
  fun `Test 14 day FTR sentence type and 28 day recall`() {
    val result = validationService.validateBeforeCalculation(
      VALID_FTR_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(FTR_14_DAY_SENTENCE).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
        fixedTermRecallDetails = FTR_DETAILS_28,
      ),
      USER_INPUTS,
    )

    assertThat(result).isEqualTo(listOf(ValidationMessage(FTR_TYPE_14_DAYS_BUT_LENGTH_IS_28)))
  }

  @Test
  fun `Test 28 day FTR sentence type and 14 day recall`() {
    val result = validationService.validateBeforeCalculation(
      VALID_FTR_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(FTR_28_DAY_SENTENCE).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
        fixedTermRecallDetails = FTR_DETAILS_14,
      ),
      USER_INPUTS,
    )

    assertThat(result).isEqualTo(listOf(ValidationMessage(FTR_TYPE_28_DAYS_BUT_LENGTH_IS_14)))
  }

  @Test
  fun `Test max sentence greater than 12 Months and recall length is 14`() {
    val result = validationService.validateBeforeCalculation(
      BOOKING.copy(
        fixedTermRecallDetails = FTR_DETAILS_14,
        sentences = listOf(FTR_SDS_SENTENCE.copy(duration = FIVE_YEAR_DURATION)),
      ),
    )

    assertThat(result).isEqualTo(listOf(ValidationMessage(FTR_14_DAYS_SENTENCE_GE_12_MONTHS)))
  }

  @Nested
  @DisplayName("Fixed term recall related validation tests")
  inner class FixedTermRecallTest {

    @Nested
    @DisplayName("Validation of source data for FTRs")
    inner class FTRSourceDataTest {
      @Test
      fun `Test FTR validation precalc`() {
        val result = validationService.validateBeforeCalculation(
          VALID_FTR_SOURCE_DATA.copy(sentenceAndOffences = listOf(FTR_14_DAY_SENTENCE, FTR_28_DAY_SENTENCE).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) }),
          USER_INPUTS,
        )

        assertThat(result).isEqualTo(listOf(ValidationMessage(FTR_SENTENCES_CONFLICT_WITH_EACH_OTHER)))
      }

      @Test
      fun `Test 14 day FTR sentence type and 28 day recall`() {
        val result = validationService.validateBeforeCalculation(
          VALID_FTR_SOURCE_DATA.copy(
            sentenceAndOffences = listOf(FTR_14_DAY_SENTENCE).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
            fixedTermRecallDetails = FTR_DETAILS_28,
          ),
          USER_INPUTS,
        )

        assertThat(result).isEqualTo(listOf(ValidationMessage(FTR_TYPE_14_DAYS_BUT_LENGTH_IS_28)))
      }

      @Test
      fun `Test 28 day FTR sentence type and 14 day recall`() {
        val result = validationService.validateBeforeCalculation(
          VALID_FTR_SOURCE_DATA.copy(
            sentenceAndOffences = listOf(FTR_28_DAY_SENTENCE).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
            fixedTermRecallDetails = FTR_DETAILS_14,
          ),
          USER_INPUTS,
        )

        assertThat(result).isEqualTo(listOf(ValidationMessage(FTR_TYPE_28_DAYS_BUT_LENGTH_IS_14)))
      }
    }

    @Nested
    @DisplayName("Validation of booking pre-calc")
    inner class FTRBeforeCalcBookingTest {
      @Test
      fun `Test max sentence greater than 12 Months and recall length is 14`() {
        val result = validationService.validateBeforeCalculation(
          BOOKING.copy(
            fixedTermRecallDetails = FTR_DETAILS_14,
            sentences = listOf(FTR_SDS_SENTENCE.copy(duration = FIVE_YEAR_DURATION)),
          ),
        )

        assertThat(result).isEqualTo(listOf(ValidationMessage(FTR_14_DAYS_SENTENCE_GE_12_MONTHS)))
      }

      @Test
      fun `Test max sentence less than 12 Months and recall length is 28`() {
        val result = validationService.validateBeforeCalculation(
          BOOKING.copy(
            fixedTermRecallDetails = FTR_DETAILS_28,
            sentences = listOf(
              FTR_SDS_SENTENCE.copy(
                duration = NINE_MONTH_DURATION,
                identifier = UUID.randomUUID(),
                consecutiveSentenceUUIDs = emptyList(),
              ),
            ),
          ),
        )

        assertThat(result).isEqualTo(
          listOf(
            ValidationMessage(FTR_28_DAYS_SENTENCE_LT_12_MONTHS),
            ValidationMessage(FTR_TYPE_28_DAYS_SENTENCE_LT_12_MONTHS),
          ),
        )
      }

      @Test
      fun `Test max sentence less than 12 Months and recall length is 28 and is in consec chain does not generate messages `() {
        val result = validationService.validateBeforeCalculation(
          BOOKING.copy(
            fixedTermRecallDetails = FTR_DETAILS_28,
            sentences = listOf(FTR_SDS_SENTENCE.copy(duration = NINE_MONTH_DURATION)),
          ),
        )

        assertThat(result).isEmpty()
      }

      @Test
      fun `Test 14 day FTR sentence and duration greater than 12 months`() {
        val result = validationService.validateBeforeCalculation(
          BOOKING.copy(
            fixedTermRecallDetails = FTR_DETAILS_28,
            sentences = listOf(FTR_SDS_SENTENCE.copy(recallType = FIXED_TERM_RECALL_14, duration = FIVE_YEAR_DURATION)),
          ),
        )

        assertThat(result).isEqualTo(listOf(ValidationMessage(FTR_TYPE_14_DAYS_SENTENCE_GE_12_MONTHS)))
      }
    }

    @Nested
    @DisplayName("Validation of booking after the calc")
    inner class FTRAfterCalcBookingTest {

      @Test
      fun `Test 14 day FTR sentence and aggregate duration greater than 12 months`() {
        val consecutiveSentenceOne = FTR_SDS_SENTENCE.copy(
          recallType = FIXED_TERM_RECALL_14,
          duration = FIVE_YEAR_DURATION,
          consecutiveSentenceUUIDs = emptyList(),
        )
        val consecutiveSentenceTwo = FTR_SDS_SENTENCE.copy(
          identifier = UUID.randomUUID(),
          recallType = FIXED_TERM_RECALL_14,
          consecutiveSentenceUUIDs = listOf(FTR_SDS_SENTENCE.identifier),
        )
        consecutiveSentenceOne.sentenceCalculation = SENTENCE_CALCULATION
        consecutiveSentenceTwo.sentenceCalculation = SENTENCE_CALCULATION
        var workingBooking = BOOKING.copy(
          fixedTermRecallDetails = FTR_DETAILS_14,
          sentences = listOf(
            consecutiveSentenceOne,
            consecutiveSentenceTwo,
          ),
        )
        workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)
        val result = validationService.validateBookingAfterCalculation(
          workingBooking,
        )

        assertThat(result).isEqualTo(
          listOf(
            ValidationMessage(FTR_14_DAYS_AGGREGATE_GE_12_MONTHS),
            ValidationMessage(FTR_TYPE_14_DAYS_AGGREGATE_GE_12_MONTHS),
          ),
        )
      }

      @Test
      fun `Test 28 day FTR sentence and aggregate duration less than 12 months`() {
        val consecutiveSentenceOne = FTR_SDS_SENTENCE.copy(
          recallType = FIXED_TERM_RECALL_14,
          duration = ONE_MONTH_DURATION,
          consecutiveSentenceUUIDs = emptyList(),
        )
        val consecutiveSentenceTwo = FTR_SDS_SENTENCE.copy(
          recallType = FIXED_TERM_RECALL_14,
          identifier = UUID.randomUUID(),
          duration = ONE_MONTH_DURATION,
          consecutiveSentenceUUIDs = listOf(FTR_SDS_SENTENCE.identifier),
        )
        consecutiveSentenceOne.sentenceCalculation = SENTENCE_CALCULATION
        consecutiveSentenceTwo.sentenceCalculation = SENTENCE_CALCULATION
        var workingBooking = BOOKING.copy(
          fixedTermRecallDetails = FTR_DETAILS_28,
          sentences = listOf(
            consecutiveSentenceOne,
            consecutiveSentenceTwo,
          ),
        )
        workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)
        val result = validationService.validateBookingAfterCalculation(
          workingBooking,
        )

        assertThat(result).isEqualTo(listOf(ValidationMessage(FTR_28_DAYS_AGGREGATE_LT_12_MONTHS)))
      }

      @Test
      fun `Test 28 day FTR type sentence and aggregate duration less than 12 months`() {
        val consecutiveSentenceOne = FTR_SDS_SENTENCE.copy(
          recallType = FIXED_TERM_RECALL_28,
          duration = ONE_MONTH_DURATION,
          consecutiveSentenceUUIDs = emptyList(),
        )
        val consecutiveSentenceTwo = FTR_SDS_SENTENCE.copy(
          recallType = FIXED_TERM_RECALL_28,
          duration = ONE_MONTH_DURATION,
          identifier = UUID.randomUUID(),
          consecutiveSentenceUUIDs = listOf(FTR_SDS_SENTENCE.identifier),
        )
        consecutiveSentenceOne.sentenceCalculation = SENTENCE_CALCULATION
        consecutiveSentenceTwo.sentenceCalculation = SENTENCE_CALCULATION
        var workingBooking = BOOKING.copy(
          fixedTermRecallDetails = FTR_DETAILS_28,
          sentences = listOf(
            consecutiveSentenceOne,
            consecutiveSentenceTwo,
          ),
        )
        workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)
        val result = validationService.validateBookingAfterCalculation(
          workingBooking,
        )

        assertThat(result).isEqualTo(
          listOf(
            ValidationMessage(FTR_28_DAYS_AGGREGATE_LT_12_MONTHS),
            ValidationMessage(FTR_TYPE_28_DAYS_AGGREGATE_LT_12_MONTHS),
          ),
        )
      }

      // Copying this
      @Test
      fun `Test 14 day aggregate Type sentence and aggregate duration greater than 12 months`() {
        val consecutiveSentenceOne = FTR_SDS_SENTENCE.copy(
          recallType = FIXED_TERM_RECALL_14,
          duration = ONE_MONTH_DURATION,
          consecutiveSentenceUUIDs = emptyList(),
        )
        val consecutiveSentenceTwo = FTR_SDS_SENTENCE.copy(
          recallType = FIXED_TERM_RECALL_14,
          duration = FIVE_YEAR_DURATION,
          identifier = UUID.randomUUID(),
          consecutiveSentenceUUIDs = listOf(FTR_SDS_SENTENCE.identifier),
        )
        consecutiveSentenceOne.sentenceCalculation = SENTENCE_CALCULATION
        consecutiveSentenceTwo.sentenceCalculation = SENTENCE_CALCULATION
        var workingBooking = BOOKING.copy(
          fixedTermRecallDetails = FTR_DETAILS_28,
          sentences = listOf(
            consecutiveSentenceOne,
            consecutiveSentenceTwo,
          ),
        )
        workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)
        val result = validationService.validateBookingAfterCalculation(
          workingBooking,
        )

        assertThat(result).isEqualTo(listOf(ValidationMessage(FTR_TYPE_14_DAYS_AGGREGATE_GE_12_MONTHS)))
      }
    }

    @Nested
    @DisplayName("DTO Recall validation tests")
    inner class DTORecallValidationTests {
      @Test
      fun `Test DTO with breach of supervision requirements returns validation message`() {
        val sentenceAndOffences = validSdsSentence.copy(
          sentenceCalculationType = SentenceCalculationType.DTO.name,
          terms = listOf(
            SentenceTerms(5, 0, 0, 0, SentenceTerms.BREACH_OF_SUPERVISION_REQUIREMENTS_TERM_CODE),
          ),
        )
        val result = validationService.validateBeforeCalculation(
          PrisonApiSourceData(
            sentenceAndOffences = listOf(sentenceAndOffences).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = BookingAndSentenceAdjustments(emptyList(), emptyList()),
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )
        assertThat(result).containsExactly(ValidationMessage(ValidationCode.DTO_RECALL))
      }

      @Test
      fun `Test DTO with breach due to imprisonable offence returns validation message`() {
        val sentenceAndOffences = validSdsSentence.copy(
          sentenceCalculationType = SentenceCalculationType.DTO.name,
          terms = listOf(
            SentenceTerms(5, 0, 0, 0, SentenceTerms.BREACH_DUE_TO_IMPRISONABLE_OFFENCE_TERM_CODE),
          ),
        )

        val result = validationService.validateBeforeCalculation(
          PrisonApiSourceData(
            sentenceAndOffences = listOf(sentenceAndOffences).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = BookingAndSentenceAdjustments(emptyList(), emptyList()),
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )
        assertThat(result).containsExactly(ValidationMessage(ValidationCode.DTO_RECALL))
      }

      @Test
      fun `Test DTO with Imprisonment term code returns no validation message`() {
        val sentenceAndOffences = validSdsSentence.copy(
          sentenceCalculationType = SentenceCalculationType.DTO.name,
          terms = listOf(
            SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          ),
        )
        val result = validationService.validateBeforeCalculation(
          PrisonApiSourceData(
            sentenceAndOffences = listOf(sentenceAndOffences).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = BookingAndSentenceAdjustments(emptyList(), emptyList()),
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )
        assertThat(result).isEmpty()
      }

      @Test
      fun `Test DTO_ORA with breach of supervision requirements returns validation message`() {
        val sentenceAndOffences = validSdsSentence.copy(
          sentenceCalculationType = SentenceCalculationType.DTO_ORA.name,
          terms = listOf(
            SentenceTerms(5, 0, 0, 0, SentenceTerms.BREACH_OF_SUPERVISION_REQUIREMENTS_TERM_CODE),
          ),
        )
        val result = validationService.validateBeforeCalculation(
          PrisonApiSourceData(
            sentenceAndOffences = listOf(sentenceAndOffences).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = BookingAndSentenceAdjustments(emptyList(), emptyList()),
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )
        assertThat(result).containsExactly(ValidationMessage(ValidationCode.DTO_RECALL))
      }

      @Test
      fun `Test DTO_ORA with breach due to imprisonable offence returns validation message`() {
        val sentenceAndOffences = validSdsSentence.copy(
          sentenceCalculationType = SentenceCalculationType.DTO_ORA.name,
          terms = listOf(
            SentenceTerms(5, 0, 0, 0, SentenceTerms.BREACH_DUE_TO_IMPRISONABLE_OFFENCE_TERM_CODE),
          ),
        )
        val result = validationService.validateBeforeCalculation(
          PrisonApiSourceData(
            sentenceAndOffences = listOf(sentenceAndOffences).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = BookingAndSentenceAdjustments(emptyList(), emptyList()),
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )
        assertThat(result).containsExactly(ValidationMessage(ValidationCode.DTO_RECALL))
      }

      @Test
      fun `Test DTO_ORA with Imprisonment term code returns no validation message`() {
        val sentenceAndOffences = validSdsSentence.copy(
          sentenceCalculationType = SentenceCalculationType.DTO_ORA.name,
          terms = listOf(
            SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
          ),
        )
        val result = validationService.validateBeforeCalculation(
          PrisonApiSourceData(
            sentenceAndOffences = listOf(sentenceAndOffences).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = BookingAndSentenceAdjustments(emptyList(), emptyList()),
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )
        assertThat(result).isEmpty()
      }

      @Test
      fun `Test non-DTO with breach of supervision requirements doesn't return DTO Recall validation message`() {
        val sentenceAndOffences = validSdsSentence.copy(
          terms = listOf(
            SentenceTerms(5, 0, 0, 0, SentenceTerms.BREACH_OF_SUPERVISION_REQUIREMENTS_TERM_CODE),
          ),
        )
        val result = validationService.validateBeforeCalculation(
          PrisonApiSourceData(
            sentenceAndOffences = listOf(sentenceAndOffences).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = BookingAndSentenceAdjustments(emptyList(), emptyList()),
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )
        assertThat(result).doesNotContain(ValidationMessage(ValidationCode.DTO_RECALL))
      }
    }

    @Nested
    @DisplayName("Future dated validation")
    inner class FutureDatedAdjustmentValidation {
      @Test
      fun `Test UAL with from and to date`() {
        val adjustment = BookingAndSentenceAdjustments(
          listOf(
            BookingAdjustment(
              active = true,
              fromDate = LocalDate.now().minusDays(10),
              toDate = LocalDate.now().plusDays(10),
              numberOfDays = 20,
              type = BookingAdjustmentType.UNLAWFULLY_AT_LARGE,
            ),
          ),
          emptyList(),
        )
        val result = validationService.validateBeforeCalculation(
          PrisonApiSourceData(
            sentenceAndOffences = listOf(SentenceAndOffenceWithReleaseArrangements(validSdsSentence, false, SDSEarlyReleaseExclusionType.NO)),
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = adjustment,
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )

        assertThat(result).isEqualTo(
          listOf(ValidationMessage(ADJUSTMENT_FUTURE_DATED_UAL)),
        )
      }

      @Test
      fun `Test UAL with only from date`() {
        val adjustment = BookingAndSentenceAdjustments(
          listOf(
            BookingAdjustment(
              active = true,
              fromDate = LocalDate.now().plusDays(10),
              toDate = null,
              numberOfDays = 20,
              type = BookingAdjustmentType.UNLAWFULLY_AT_LARGE,
            ),
          ),
          emptyList(),
        )
        val result = validationService.validateBeforeCalculation(
          PrisonApiSourceData(
            sentenceAndOffences = listOf(SentenceAndOffenceWithReleaseArrangements(validSdsSentence, false, SDSEarlyReleaseExclusionType.NO)),
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = adjustment,
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )

        assertThat(result).isEqualTo(
          listOf(ValidationMessage(ADJUSTMENT_FUTURE_DATED_UAL)),
        )
      }

      @Test
      fun `Test ADA`() {
        val adjustment = BookingAndSentenceAdjustments(
          listOf(
            BookingAdjustment(
              active = true,
              fromDate = LocalDate.now().plusDays(10),
              toDate = null,
              numberOfDays = 20,
              type = BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED,
            ),
          ),
          emptyList(),
        )
        val result = validationService.validateBeforeCalculation(
          PrisonApiSourceData(
            sentenceAndOffences = listOf(SentenceAndOffenceWithReleaseArrangements(validSdsSentence, false, SDSEarlyReleaseExclusionType.NO)),
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = adjustment,
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )

        assertThat(result).isEqualTo(
          listOf(ValidationMessage(ADJUSTMENT_FUTURE_DATED_ADA)),
        )
      }

      @Test
      fun `Test RADA`() {
        val adjustment = BookingAndSentenceAdjustments(
          listOf(
            BookingAdjustment(
              active = true,
              fromDate = LocalDate.now().plusDays(10),
              toDate = null,
              numberOfDays = 20,
              type = BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED,
            ),
          ),
          emptyList(),
        )
        val result = validationService.validateBeforeCalculation(
          PrisonApiSourceData(
            sentenceAndOffences = listOf(SentenceAndOffenceWithReleaseArrangements(validSdsSentence, false, SDSEarlyReleaseExclusionType.NO)),
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = adjustment,
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )

        assertThat(result).isEqualTo(
          listOf(ValidationMessage(ADJUSTMENT_FUTURE_DATED_RADA)),
        )
      }
    }
  }

  @Test
  fun `Test that a validation error is generated for sentences with missing offence dates when validating for manual entry`() {
    val result = validationService.validateSentenceForManualEntry(listOf(sentenceWithMissingOffenceDates))
    assertThat(result).containsExactly(ValidationMessage(ValidationCode.OFFENCE_MISSING_DATE, listOf("1", "2")))
  }

  @Test
  fun `If a sentence has been normalised then it doesn't trigger consecutive sentence warning`() {
    val sentence1 = SentenceAndOffenceWithReleaseArrangements(
      bookingId = 1L,
      sentenceSequence = 1,
      lineSequence = 1,
      caseSequence = 1,
      sentenceDate = FIRST_MAY_2018,
      terms = listOf(
        SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      ),
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceCategory = "2003",
      sentenceStatus = "a",
      sentenceTypeDescription = "This is a sentence type",
      offence = OffenderOffence(
        offenderChargeId = 1L,
        offenceStartDate = LocalDate.of(2015, 1, 1),
        offenceCode = "Dummy Offence",
        offenceDescription = "A Dummy description",
      ),
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = 3,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    val sentence2 = sentence1.copy(
      offence = OffenderOffence(
        offenderChargeId = 2L,
        offenceStartDate = LocalDate.of(2015, 1, 1),
        offenceCode = "Another Dummy Offence",
        offenceDescription = "A Dummy description",
      ),
    )
    val sentence3 = SentenceAndOffenceWithReleaseArrangements(
      bookingId = 1L,
      sentenceSequence = 3,
      lineSequence = 1,
      caseSequence = 2,
      sentenceDate = FIRST_MAY_2018,
      terms = listOf(
        SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      ),
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceCategory = "2003",
      sentenceStatus = "a",
      sentenceTypeDescription = "This is a sentence type",
      offence = OffenderOffence(
        offenderChargeId = 3L,
        offenceStartDate = LocalDate.of(2015, 1, 1),
        offenceCode = "And Another Dummy Offence",
        offenceDescription = "A Dummy description",
      ),
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = 1,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )

    val result = validationService.validateBeforeCalculation(
      PrisonApiSourceData(
        listOf(sentence1, sentence2, sentence3),
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(),
        null,
      ),
      USER_INPUTS,
    )
    assertThat(result).isEmpty()
  }

  @Test
  fun `If a sentence has not been normalised then it can trigger consecutive sentence warning`() {
    val sentence1 = SentenceAndOffenceWithReleaseArrangements(
      bookingId = 1L,
      sentenceSequence = 1,
      lineSequence = 1,
      caseSequence = 1,
      sentenceDate = FIRST_MAY_2018,
      terms = listOf(
        SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      ),
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceCategory = "2003",
      sentenceStatus = "a",
      sentenceTypeDescription = "This is a sentence type",
      offence = OffenderOffence(
        offenderChargeId = 1L,
        offenceStartDate = LocalDate.of(2015, 1, 1),
        offenceCode = "Dummy Offence",
        offenceDescription = "A Dummy description",
      ),
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = 3,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    val sentence2 = SentenceAndOffenceWithReleaseArrangements(
      bookingId = 1L,
      sentenceSequence = 2,
      lineSequence = 2,
      caseSequence = 1,
      sentenceDate = FIRST_MAY_2018,
      terms = listOf(
        SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      ),
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceCategory = "2003",
      sentenceStatus = "a",
      sentenceTypeDescription = "This is a sentence type",
      offence = OffenderOffence(
        offenderChargeId = 1L,
        offenceStartDate = LocalDate.of(2015, 1, 1),
        offenceCode = "Another Dummy Offence",
        offenceDescription = "A Dummy description",
      ),
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = 3,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    val sentence3 = SentenceAndOffenceWithReleaseArrangements(
      bookingId = 1L,
      sentenceSequence = 3,
      lineSequence = 1,
      caseSequence = 2,
      sentenceDate = FIRST_MAY_2018,
      terms = listOf(
        SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      ),
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceCategory = "2003",
      sentenceStatus = "a",
      sentenceTypeDescription = "This is a sentence type",
      offence = OffenderOffence(
        offenderChargeId = 3L,
        offenceStartDate = LocalDate.of(2015, 1, 1),
        offenceCode = "And Another Dummy Offence",
        offenceDescription = "A Dummy description",
      ),
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = 1,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )

    val result = validationService.validateBeforeCalculation(
      PrisonApiSourceData(
        listOf(sentence1, sentence2, sentence3),
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(),
        null,
      ),
      USER_INPUTS,
    )
    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(
          ValidationCode.MULTIPLE_SENTENCES_CONSECUTIVE_TO,
          listOf("2", "1"),
        ),
      ),
    )
  }

  @Test
  fun `Validate that SDS sentences are unsupported for before SDS early release is implemented`() {
    val validationService = ValidationService(SentencesExtractionService(), FeatureToggles(sdsEarlyReleaseUnsupported = true), SDS40_TRANCHE_ONE)

    val result = validationService.validateBeforeCalculation(
      PrisonApiSourceData(
        listOf(validSdsSentence).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(),
        null,
      ),
      USER_INPUTS,
    )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(
          ValidationCode.SDS_EARLY_RELEASE_UNSUPPORTED,
        ),
      ),
    )
  }

  @Test
  fun `Validate that SDS+ sentences are supported for before SDS early release is implemented`() {
    val validationService = ValidationService(SentencesExtractionService(), FeatureToggles(sdsEarlyReleaseUnsupported = true), SDS40_TRANCHE_ONE)

    val result = validationService.validateBeforeCalculation(
      PrisonApiSourceData(
        listOf(validSdsSentence).map { SentenceAndOffenceWithReleaseArrangements(it, true, SDSEarlyReleaseExclusionType.NO) },
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(),
        null,
      ),
      USER_INPUTS,
    )

    assertThat(result.isEmpty()).isTrue
  }

  @Test
  fun `Test LA_ORA with CRD after tranche commencement returns a validation error`() {
    val validationService = ValidationService(SentencesExtractionService(), FeatureToggles(sdsEarlyRelease = true), SDS40_TRANCHE_ONE)
    val laOraSentence = LA_ORA.copy()

    laOraSentence.sentenceCalculation = SENTENCE_CALCULATION.copy(
      unadjustedHistoricDeterminateReleaseDate = LocalDate.of(2024, 9, 11),
    )
    var workingBooking = BOOKING.copy(
      sentences = listOf(
        laOraSentence,
      ),
      adjustments = Adjustments(),
    )

    workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)

    val testCalculationResult = CalculationResult(
      dates = mapOf(ReleaseDateType.TUSED to LocalDate.now(), ReleaseDateType.CRD to LocalDate.of(2024, 9, 11)),
      effectiveSentenceLength = Period.of(0, 9, 0),
    )

    val result = validationService.validateBookingAfterCalculation(
      workingBooking,
      testCalculationResult,
    )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(UNSUPPORTED_SDS40_RECALL_SENTENCE_TYPE),
      ),
    )
  }

  @Test
  fun `Test LA_ORA with CRD before tranche commencement returns no error`() {
    val validationService = ValidationService(SentencesExtractionService(), FeatureToggles(sdsEarlyRelease = true), SDS40_TRANCHE_ONE)
    val laOraSentence = LA_ORA.copy()

    laOraSentence.sentenceCalculation = SENTENCE_CALCULATION.copy(
      unadjustedHistoricDeterminateReleaseDate = LocalDate.of(2024, 9, 9),
    )
    var workingBooking = BOOKING.copy(
      sentences = listOf(
        laOraSentence,
      ),
      adjustments = Adjustments(),
    )

    workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)

    val testCalculationResult = CalculationResult(
      dates = mapOf(ReleaseDateType.TUSED to LocalDate.now(), ReleaseDateType.CRD to LocalDate.of(2024, 9, 9)),
      effectiveSentenceLength = Period.of(0, 9, 0),
    )

    val result = validationService.validateBookingAfterCalculation(
      workingBooking,
      testCalculationResult,
    )

    assertThat(result).isEmpty()
  }

  @Test
  fun `Test LA_ORA with no TUSED after tranche commencement returns no error`() {
    val validationService = ValidationService(SentencesExtractionService(), FeatureToggles(sdsEarlyRelease = true), SDS40_TRANCHE_ONE)
    val laOraSentence = LA_ORA.copy()

    laOraSentence.sentenceCalculation = SENTENCE_CALCULATION.copy(
      unadjustedHistoricDeterminateReleaseDate = LocalDate.of(2024, 9, 11),
    )
    var workingBooking = BOOKING.copy(
      sentences = listOf(
        laOraSentence,
      ),
      adjustments = Adjustments(),
    )

    workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)

    val testCalculationResult = CalculationResult(
      dates = mapOf(ReleaseDateType.CRD to LocalDate.of(2024, 9, 11)),
      effectiveSentenceLength = Period.of(0, 9, 0),
    )

    val result = validationService.validateBookingAfterCalculation(
      workingBooking,
      testCalculationResult,
    )

    assertThat(result).isEmpty()
  }

  private fun createSentenceChain(
    start: AbstractSentence,
    chain: MutableList<AbstractSentence>,
    sentencesByPrevious: Map<UUID, List<AbstractSentence>>,
    chains: MutableList<MutableList<AbstractSentence>> = mutableListOf(mutableListOf()),
  ) {
    val originalChain = chain.toMutableList()
    sentencesByPrevious[start.identifier]?.forEachIndexed { index, it ->
      if (index == 0) {
        chain.add(it)
        createSentenceChain(it, chain, sentencesByPrevious, chains)
      } else {
        // This sentence has two sentences consecutive to it. This is not allowed in practice, however it can happen
        // when a sentence in NOMIS has multiple offices, which means it becomes multiple sentences in our model.
        val chainCopy = originalChain.toMutableList()
        chains.add(chainCopy)
        chainCopy.add(it)
        createSentenceChain(it, chainCopy, sentencesByPrevious, chains)
      }
    }
  }

  private companion object {
    val FIRST_MAY_2018: LocalDate = LocalDate.of(2018, 5, 1)
    val FIRST_MAY_2021: LocalDate = LocalDate.of(2021, 5, 1)
    private const val LINE_SEQ = 2
    private const val CASE_SEQ = 1
    val ONE_MONTH_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 1L, YEARS to 0L))
    val FIVE_YEAR_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L))
    val NINE_MONTH_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 9L, YEARS to 0L))
    val FIRST_JAN_2015: LocalDate = LocalDate.of(2015, 1, 1)
    val DOB: LocalDate = LocalDate.of(1980, 1, 1)
    val SDS40_TRANCHE_ONE = TrancheOne(LocalDate.of(2024, 9, 10))

    const val PRISONER_ID = "A123456A"
    const val SEQUENCE = 153
    const val LINE_SEQUENCE = 154
    const val CASE_SEQUENCE = 155
    const val COMPANION_BOOKING_ID = 123456L
    const val CONSECUTIVE_TO = 99
    const val OFFENCE_CODE = "RR1"
    val returnToCustodyDate = ReturnToCustodyDate(COMPANION_BOOKING_ID, LocalDate.of(2022, 3, 15))
    private val USER_INPUTS = CalculationUserInputs()
    private val VALID_PRISONER = PrisonerDetails(offenderNo = "", bookingId = 1, dateOfBirth = LocalDate.of(1, 2, 3))
    private val VALID_ADJUSTMENTS = BookingAndSentenceAdjustments(emptyList(), emptyList())
    private const val BOOKING_ID = 100091L
    private val RETURN_TO_CUSTODY_DATE = LocalDate.of(2022, 3, 15)
    private val FTR_DETAILS_14 = FixedTermRecallDetails(BOOKING_ID, RETURN_TO_CUSTODY_DATE, 14)
    private val FTR_DETAILS_28 = FixedTermRecallDetails(BOOKING_ID, RETURN_TO_CUSTODY_DATE, 28)
    private val FTR_14_DAY_SENTENCE = NormalisedSentenceAndOffence(
      bookingId = 1L,
      sentenceSequence = 7,
      lineSequence = LINE_SEQ,
      caseSequence = CASE_SEQ,
      sentenceDate = FIRST_MAY_2018,
      terms = listOf(
        SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      ),
      sentenceCalculationType = FTR_14_ORA.primaryName!!,
      sentenceCategory = "2003",
      sentenceStatus = "a",
      sentenceTypeDescription = "This is a sentence type",
      offence = OffenderOffence(
        1,
        LocalDate.of(2015, 4, 1),
        null,
        "A123456",
        "TEST OFFENCE 2",
      ),
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = null,
    )
    private val FTR_28_DAY_SENTENCE = NormalisedSentenceAndOffence(
      bookingId = 1L,
      sentenceSequence = 7,
      lineSequence = LINE_SEQ,
      caseSequence = CASE_SEQ,
      sentenceDate = FIRST_MAY_2018,
      terms = listOf(
        SentenceTerms(5, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      ),
      sentenceCalculationType = FTR.name,
      sentenceCategory = "2003",
      sentenceStatus = "a",
      sentenceTypeDescription = "This is a sentence type",
      offence = OffenderOffence(
        1,
        LocalDate.of(2015, 4, 1),
        null,
        "A123456",
        "TEST OFFENCE 2",
      ),
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = null,
    )
    private val VALID_FTR_SOURCE_DATA = PrisonApiSourceData(
      sentenceAndOffences = listOf(FTR_14_DAY_SENTENCE).map { SentenceAndOffenceWithReleaseArrangements(it, false, SDSEarlyReleaseExclusionType.NO) },
      prisonerDetails = VALID_PRISONER,
      bookingAndSentenceAdjustments = VALID_ADJUSTMENTS,
      returnToCustodyDate = null,
      fixedTermRecallDetails = FTR_DETAILS_14,
    )
    private val FTR_SDS_SENTENCE = StandardDeterminateSentence(
      sentencedAt = FIRST_JAN_2015,
      duration = FIVE_YEAR_DURATION,
      offence = Offence(
        committedAt = FIRST_JAN_2015,
        offenceCode = OFFENCE_CODE,
      ),
      identifier = UUID.nameUUIDFromBytes(("$COMPANION_BOOKING_ID-$SEQUENCE").toByteArray()),
      consecutiveSentenceUUIDs = mutableListOf(
        UUID.nameUUIDFromBytes(("$COMPANION_BOOKING_ID-$CONSECUTIVE_TO").toByteArray()),
      ),
      lineSequence = LINE_SEQUENCE,
      caseSequence = CASE_SEQUENCE,
      recallType = FIXED_TERM_RECALL_28,
      isSDSPlus = true,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )

    private val LA_ORA = StandardDeterminateSentence(
      sentencedAt = LocalDate.of(2024, 1, 10),
      duration = Duration(mapOf(MONTHS to 18L)),
      offence = Offence(
        committedAt = LocalDate.of(2023, 10, 10),
        offenceCode = OFFENCE_CODE,
      ),
      identifier = UUID.nameUUIDFromBytes(("$COMPANION_BOOKING_ID-$SEQUENCE").toByteArray()),
      lineSequence = LINE_SEQUENCE,
      caseSequence = CASE_SEQUENCE,
      recallType = STANDARD_RECALL,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )

    val ONE_DAY_DURATION = Duration(mapOf(DAYS to 1L))
    val OFFENCE = Offence(LocalDate.of(2020, 1, 1))
    val STANDARD_SENTENCE = StandardDeterminateSentence(OFFENCE, ONE_DAY_DURATION, LocalDate.of(2020, 1, 1), isSDSPlus = false, hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO)
    val SENTENCE_CALCULATION = SentenceCalculation(
      STANDARD_SENTENCE,
      3,
      4.0,
      4,
      4,
      FIRST_MAY_2018,
      FIRST_MAY_2018,
      FIRST_MAY_2018,
      1,
      FIRST_MAY_2018,
      false,
      Adjustments(
        mutableMapOf(
          REMAND to mutableListOf(
            Adjustment(
              numberOfDays = 1,
              appliesToSentencesFrom = FIRST_MAY_2018,
            ),
          ),
        ),
      ),
      FIRST_MAY_2018,
    )
    private val BOOKING = Booking(
      bookingId = 123456,
      returnToCustodyDate = returnToCustodyDate.returnToCustodyDate,
      offender = Offender(
        dateOfBirth = DOB,
        reference = PRISONER_ID,
      ),
      sentences = mutableListOf(
        FTR_SDS_SENTENCE,
      ),
      adjustments = Adjustments(
        mutableMapOf(
          UNLAWFULLY_AT_LARGE to mutableListOf(
            Adjustment(
              appliesToSentencesFrom = FIRST_JAN_2015.minusDays(6),
              numberOfDays = 5,
              fromDate = FIRST_JAN_2015.minusDays(6),
              toDate = FIRST_JAN_2015.minusDays(1),
            ),
          ),
          REMAND to mutableListOf(
            Adjustment(
              appliesToSentencesFrom = FIRST_JAN_2015,
              numberOfDays = 6,
              fromDate = FIRST_JAN_2015.minusDays(7),
              toDate = FIRST_JAN_2015.minusDays(1),
            ),
          ),
        ),
      ),
    )
  }
}
