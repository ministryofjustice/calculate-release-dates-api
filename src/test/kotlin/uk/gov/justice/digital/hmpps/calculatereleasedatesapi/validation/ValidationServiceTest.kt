package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.springframework.context.annotation.Profile
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.STANDARD_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateTypes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.LAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType.SPECIAL_REMISSION
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType.TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType.TIME_SPENT_AS_AN_APPEAL_APPLICANT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType.TIME_SPENT_IN_CUSTODY_ABROAD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.FTR
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.FTR_14_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ManageOffencesService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentencesExtractionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.BookingHelperTest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_RADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_UAL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_CONSECUTIVE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_CONSECUTIVE_TO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_MISSING_FINE_AMOUNT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_WITH_PAYMENTS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.DTO_CONSECUTIVE_TO_SENTENCE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.DTO_HAS_SENTENCE_CONSECUTIVE_TO_IT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_14_DAYS_AGGREGATE_GE_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_14_DAYS_SENTENCE_GE_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_28_DAYS_AGGREGATE_LT_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_28_DAYS_SENTENCE_LT_12_MONTHS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.FTR_NO_RETURN_TO_CUSTODY_DATE
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_SDS40_CONSECUTIVE_SDS_BETWEEN_TRANCHE_COMMENCEMENTS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_SENTENCE_TYPE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ZERO_IMPRISONMENT_TERM
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.UUID

@Profile("tests")
class ValidationServiceTest {
  val validationService =
    getActiveValidationService(
      trancheConfiguration = TRANCHE_CONFIGURATION,
      sentencesExtractionService = SentencesExtractionService(),
    )

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

  private val timeSpentInCustodyAbroadSentenceAdjustment = BookingAndSentenceAdjustments(
    emptyList(),
    listOf(
      SentenceAdjustment(
        sentenceSequence = 1,
        active = true,
        numberOfDays = 23,
        type = TIME_SPENT_IN_CUSTODY_ABROAD,
      ),
    ),
  )

  private val timeSpentAsAnAppealApplicantSentenceAdjustment = BookingAndSentenceAdjustments(
    emptyList(),
    listOf(
      SentenceAdjustment(
        sentenceSequence = 1,
        active = true,
        numberOfDays = 32,
        type = TIME_SPENT_AS_AN_APPEAL_APPLICANT,
      ),
    ),
  )

  @ParameterizedTest
  @ValueSource(strings = ["SE20512", "CJ03523"])
  fun `Test Sentences with unsupported suspended offenceCodes SE20512, CJ03523 returns validation message`(offenceCode: String) {
    // Arrange
    val invalidSentence = validSdsSentence.copy(
      offence = validSdsSentence.offence.copy(offenceCode = offenceCode),
    )

    // Act
    val result =
      validationService.validateBeforeCalculation(
        CalculationSourceData(
          listOf(
            SentenceAndOffenceWithReleaseArrangements(
              source = invalidSentence,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            ),
          ),
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
        USER_INPUTS,
      )

    // Assert
    assertThat(result).isNotEmpty
    assertThat(result[0].code).isEqualTo(ValidationCode.UNSUPPORTED_SUSPENDED_OFFENCE)
  }

  @ParameterizedTest
  @ValueSource(strings = ["PH97003", "PH97003B"])
  fun `Test Sentences with unsupported offenceCodes PH97003 before 2020 and no error message`(offenceCode: String) {
    // Arrange
    val invalidSentence = validSdsSentence.copy(
      sentenceDate = LocalDate.of(2020, 11, 30),
      offence = validSdsSentence.offence.copy(offenceCode = offenceCode, offenceStartDate = LocalDate.of(2020, 11, 1)),
    )

    // Act
    val result =
      validationService.validateBeforeCalculation(
        CalculationSourceData(
          sentenceAndOffences = listOf(
            SentenceAndOffenceWithReleaseArrangements(
              source = invalidSentence,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            ),
          ),
          prisonerDetails = VALID_PRISONER,
          bookingAndSentenceAdjustments = VALID_ADJUSTMENTS,
          offenderFinePayments = listOf(),
          returnToCustodyDate = null,
        ),
        USER_INPUTS,
      )

    // Assert
    assertThat(result).isEmpty()
  }

  @ParameterizedTest
  @ValueSource(strings = ["PH97003", "PH97003B"])
  fun `Test Sentences with unsupported offenceCodes PH97003 after Dec 2020 and inchoates to return validation message`(
    offenceCode: String,
  ) {
    // Arrange
    val invalidSentence = validSdsSentence.copy(
      sentenceDate = LocalDate.of(2020, 12, 1),
      offence = validSdsSentence.offence.copy(offenceCode = offenceCode, offenceStartDate = LocalDate.of(2020, 12, 1)),
    )

    // Act
    val result =
      validationService.validateBeforeCalculation(
        CalculationSourceData(
          listOf(
            SentenceAndOffenceWithReleaseArrangements(
              source = invalidSentence,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            ),
          ),
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
        USER_INPUTS,
      )

    // Assert
    assertThat(result).isNotEmpty
    assertThat(result[0].code).isEqualTo(ValidationCode.UNSUPPORTED_BREACH_97)
  }

  @ParameterizedTest
  @ValueSource(strings = ["CL77036"])
  fun `Test Sentences with unsupported offenceCode CL77036 returns validation message`(offenceCode: String) {
    // Arrange
    val invalidSentence = validSdsSentence.copy(
      offence = validSdsSentence.offence.copy(offenceCode = offenceCode),
    )

    // Act
    val result =
      validationService.validateBeforeCalculation(
        CalculationSourceData(
          sentenceAndOffences = listOf(
            SentenceAndOffenceWithReleaseArrangements(
              source = invalidSentence,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            ),
          ),
          prisonerDetails = VALID_PRISONER,
          bookingAndSentenceAdjustments = VALID_ADJUSTMENTS,
          offenderFinePayments = listOf(),
          returnToCustodyDate = null,
        ),
        USER_INPUTS,
      )

    // Assert
    assertThat(result).isNotEmpty
    assertThat(result[0].code).isEqualTo(ValidationCode.UNSUPPORTED_GENERIC_CONSPIRACY_OFFENCE)
  }

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
        CalculationSourceData(
          sentenceAndOffences = listOf(
            SentenceAndOffenceWithReleaseArrangements(
              source = invalidSentence,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            ),
          ),
          prisonerDetails = VALID_PRISONER,
          bookingAndSentenceAdjustments = VALID_ADJUSTMENTS,
          offenderFinePayments = listOf(),
          returnToCustodyDate = null,
        ),
        USER_INPUTS,
      )

    // Assert
    assertThat(result).isNotEmpty
    assertThat(result[0].code).isEqualTo(ValidationCode.UNSUPPORTED_OFFENCE_ENCOURAGING_OR_ASSISTING)
  }

  @ParameterizedTest
  @ValueSource(strings = ["KS97002", "SC07001", "SC07014", "FG06019", "TH68058"])
  fun `Test Sentences with supported offenceCodes shouldn't return validation message`(offenceCode: String) {
    // Arrange
    val validSentence = validSdsSentence.copy(
      offence = validSdsSentence.offence.copy(offenceCode = offenceCode),
    )

    // Act
    val result =
      validationService.validateBeforeCalculation(
        CalculationSourceData(
          listOf(
            SentenceAndOffenceWithReleaseArrangements(
              source = validSentence,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            ),
          ),
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
        USER_INPUTS,
      )

    // Assert
    assertThat(result).isEmpty()
  }

  @Test
  fun `Test Fixed Term Recall with no return to custody date should fail`() {
    val result = validationService.validateBeforeCalculation(
      VALID_FTR_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(FTR_14_DAY_SENTENCE).map {
          SentenceAndOffenceWithReleaseArrangements(
            source = it,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          )
        },
        fixedTermRecallDetails = FTR_DETAILS_NO_RTC,
      ),
      USER_INPUTS,
    )
    assertThat(result).isEqualTo(listOf(ValidationMessage(FTR_NO_RETURN_TO_CUSTODY_DATE)))
  }

  @Test
  fun `Test EDS valid sentence should pass`() {
    val result = validationService.validateBeforeCalculation(
      CalculationSourceData(
        listOf(
          SentenceAndOffenceWithReleaseArrangements(
            validEdsSentence,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
        ),
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
        CalculationSourceData(
          listOf(
            SentenceAndOffenceWithReleaseArrangements(
              source = sentence,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            ),
          ),
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
        CalculationSourceData(
          listOf(
            SentenceAndOffenceWithReleaseArrangements(
              source = sentence,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            ),
          ),
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
          ZERO_IMPRISONMENT_TERM,
          listOf(CASE_SEQ.toString(), LINE_SEQ.toString()),
        ),
      ),
    )
  }

  @Test
  fun `Test EDS sentences should have licence term`() {
    val sentence = validEdsSentence.copy(
      terms = listOf(
        SentenceTerms(1, 0, 0, 0, SentenceTerms.IMPRISONMENT_TERM_CODE),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        CalculationSourceData(
          listOf(
            SentenceAndOffenceWithReleaseArrangements(
              source = sentence,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            ),
          ),
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
          SENTENCE_HAS_NO_LICENCE_TERM,
          listOf(CASE_SEQ.toString(), LINE_SEQ.toString()),
        ),
      ),
    )
  }

  @Test
  fun `Test EDS sentences should have licence term of at least 1 year`() {
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          }.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
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
  fun `Test EDS sentences should have licence term of at least 1 year valid`() {
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          }.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
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
  fun `Test EDS sentences should have licence term of at less than 8 years`() {
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(LASPO_AR_SENTENCE_TYPE_INCORRECT, listOf(CASE_SEQ.toString(), LINE_SEQ.toString())),
      ),
    )
  }

  @Test
  fun `Test EDS sentences shouldnt have more than one licence term or imprisonment term`() {
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
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
      CalculationSourceData(
        listOf(validSopcSentence).map {
          SentenceAndOffenceWithReleaseArrangements(
            source = it,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          )
        },
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
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
      CalculationSourceData(
        listOf(validEdsSentence).map {
          SentenceAndOffenceWithReleaseArrangements(
            source = it,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          )
        },
        VALID_PRISONER,
        AdjustmentsSourceData(
          prisonApiData = BookingAndSentenceAdjustments(
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
  fun `Test SOPC sentences should have licence term of exactly 1 year`() {
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
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
    val sentences =
      listOf(
        SentenceAndOffenceWithReleaseArrangements(
          source = validSdsSentence,
          isSdsPlus = false,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
          isSDSPlusOffenceInPeriod = false,
          hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
        ),
      )
    val result =
      validationService.validateBeforeCalculation(
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
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
  fun `Test A FINE sentence is valid`() {
    val sentences = listOf(validAFineSentence)
    val result =
      validationService.validateBeforeCalculation(
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
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
  fun `Test A FINE sentence with payments is unsupported`() {
    val sentences = listOf(validAFineSentence)
    val result = validationService.validateBeforeCalculation(
      CalculationSourceData(
        sentences.map {
          SentenceAndOffenceWithReleaseArrangements(
            source = it,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          )
        },
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
        USER_INPUTS,
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(DTO_CONSECUTIVE_TO_SENTENCE),
      ),
    )
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          AdjustmentsSourceData(prisonApiData = adjustments),
          listOf(),
          null,
        ),
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
          type = TAGGED_BAIL,
        ),
      ),
    )
    val result =
      validationService.validateBeforeCalculation(
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          AdjustmentsSourceData(prisonApiData = adjustments),
          listOf(),
          null,
        ),
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
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
      CalculationSourceData(
        sentences.map {
          SentenceAndOffenceWithReleaseArrangements(
            source = it,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          )
        },
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
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
        CalculationSourceData(
          sentences.map {
            SentenceAndOffenceWithReleaseArrangements(
              source = it,
              isSdsPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSDSPlusOffenceInPeriod = false,
              hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
            )
          },
          VALID_PRISONER,
          VALID_ADJUSTMENTS,
          listOf(),
          null,
        ),
        CalculationUserInputs(),
      )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(UNSUPPORTED_SENTENCE_TYPE, listOf("1991 This is a sentence type")),
      ),
    )
  }

  @Test
  fun `Test Lawfully at Large adjustments at a booking level cause validation errors`() {
    val result = validationService.validateBeforeCalculation(
      sourceData = CalculationSourceData(
        sentenceAndOffences = listOf(
          SentenceAndOffenceWithReleaseArrangements(
            source = validSdsSentence,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
        ),
        prisonerDetails = VALID_PRISONER,
        bookingAndSentenceAdjustments = AdjustmentsSourceData(prisonApiData = lawfullyAtLargeBookingAdjustment),
        returnToCustodyDate = null,
      ),
      calculationUserInputs = USER_INPUTS,
    )

    assertThat(result).isEqualTo(
      listOf(ValidationMessage(UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE)),
    )
  }

  @Test
  fun `Test Special Remission adjustments at a booking level cause validation errors`() {
    val result = validationService.validateBeforeCalculation(
      sourceData = CalculationSourceData(
        sentenceAndOffences = listOf(
          SentenceAndOffenceWithReleaseArrangements(
            source = validSdsSentence,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
        ),
        prisonerDetails = VALID_PRISONER,
        bookingAndSentenceAdjustments = AdjustmentsSourceData(prisonApiData = specialRemissionBookingAdjustment),
        returnToCustodyDate = null,
      ),
      calculationUserInputs = CalculationUserInputs(),
    )

    assertThat(result).isEqualTo(
      listOf(ValidationMessage(UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION)),
    )
  }

  @Test
  fun `Test time spent in custody abroad adjustments throw validation errors`() {
    val result = validationService.validateBeforeCalculation(
      sourceData = CalculationSourceData(
        sentenceAndOffences = listOf(
          SentenceAndOffenceWithReleaseArrangements(
            source = validSdsSentence,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
        ),
        prisonerDetails = VALID_PRISONER,
        bookingAndSentenceAdjustments = AdjustmentsSourceData(prisonApiData = timeSpentInCustodyAbroadSentenceAdjustment),
        returnToCustodyDate = null,
      ),
      calculationUserInputs = CalculationUserInputs(),
    )

    assertThat(result).isEqualTo(
      listOf(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_TIME_SPENT_IN_CUSTODY_ABROAD)),
    )
  }

  @Test
  fun `Test time as an appeal applicant adjustments throw validation errors`() {
    val result = validationService.validateBeforeCalculation(
      sourceData = CalculationSourceData(
        sentenceAndOffences = listOf(
          SentenceAndOffenceWithReleaseArrangements(
            source = validSdsSentence,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
        ),
        prisonerDetails = VALID_PRISONER,
        bookingAndSentenceAdjustments = AdjustmentsSourceData(prisonApiData = timeSpentAsAnAppealApplicantSentenceAdjustment),
        returnToCustodyDate = null,
      ),
      calculationUserInputs = CalculationUserInputs(),
    )

    assertThat(result).isEqualTo(
      listOf(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_TIME_SPENT_AS_AN_APPEAL_APPLICANT)),
    )
  }

  @Test
  fun `Test EDS recalls supported`() {
    val result = validationService.validateBeforeCalculation(
      CalculationSourceData(
        listOf(
          SentenceAndOffenceWithReleaseArrangements(
            source = validEdsRecallSentence,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
        ),
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(),
        null,
      ),
      CalculationUserInputs(),
    )

    assertThat(result).isEmpty()
  }

  @Test
  fun `Test SOPC recalls supported`() {
    val result = validationService.validateBeforeCalculation(
      CalculationSourceData(
        listOf(
          SentenceAndOffenceWithReleaseArrangements(
            source = validSopcRecallSentence,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
        ),
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(),
        null,
      ),
      CalculationUserInputs(),
    )

    assertThat(result).isEmpty()
  }

  @Test
  fun `Test FTR validation precalc`() {
    val result = validationService.validateBeforeCalculation(
      VALID_FTR_SOURCE_DATA.copy(
        returnToCustodyDate = ReturnToCustodyDate(BOOKING_ID, FTR_DETAILS_14.returnToCustodyDate),
        sentenceAndOffences = listOf(
          FTR_14_DAY_SENTENCE,
          FTR_28_DAY_SENTENCE,
        ).map {
          SentenceAndOffenceWithReleaseArrangements(
            source = it,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          )
        },
      ),
      USER_INPUTS,
    )

    assertThat(result).isEqualTo(listOf(ValidationMessage(FTR_SENTENCES_CONFLICT_WITH_EACH_OTHER)))
  }

  @Test
  fun `Test 14 day FTR sentence type and 28 day recall`() {
    val result = validationService.validateBeforeCalculation(
      VALID_FTR_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(FTR_14_DAY_SENTENCE).map {
          SentenceAndOffenceWithReleaseArrangements(
            source = it,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          )
        },
        returnToCustodyDate = ReturnToCustodyDate(BOOKING_ID, FTR_DETAILS_28.returnToCustodyDate),
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
        sentenceAndOffences = listOf(FTR_28_DAY_SENTENCE).map {
          SentenceAndOffenceWithReleaseArrangements(
            it,
            false,
            false,
            false,
            SDSEarlyReleaseExclusionType.NO,
          )
        },
        fixedTermRecallDetails = FTR_DETAILS_14,
        returnToCustodyDate = ReturnToCustodyDate(BOOKING_ID, FTR_DETAILS_14.returnToCustodyDate),
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
        returnToCustodyDate = FTR_DETAILS_14.returnToCustodyDate,
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
          VALID_FTR_SOURCE_DATA.copy(
            sentenceAndOffences = listOf(
              FTR_14_DAY_SENTENCE,
              FTR_28_DAY_SENTENCE,
            ).map {
              SentenceAndOffenceWithReleaseArrangements(
                source = it,
                isSdsPlus = false,
                isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
                isSDSPlusOffenceInPeriod = false,
                hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
              )
            },
            returnToCustodyDate = ReturnToCustodyDate(BOOKING_ID, FTR_DETAILS_14.returnToCustodyDate),
          ),
          USER_INPUTS,
        )

        assertThat(result).isEqualTo(listOf(ValidationMessage(FTR_SENTENCES_CONFLICT_WITH_EACH_OTHER)))
      }

      @Test
      fun `Test 14 day FTR sentence type and 28 day recall`() {
        val result = validationService.validateBeforeCalculation(
          VALID_FTR_SOURCE_DATA.copy(
            sentenceAndOffences = listOf(FTR_14_DAY_SENTENCE).map {
              SentenceAndOffenceWithReleaseArrangements(
                source = it,
                isSdsPlus = false,
                isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
                isSDSPlusOffenceInPeriod = false,
                hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
              )
            },
            fixedTermRecallDetails = FTR_DETAILS_28,
            returnToCustodyDate = ReturnToCustodyDate(BOOKING_ID, FTR_DETAILS_28.returnToCustodyDate),
          ),
          USER_INPUTS,
        )

        assertThat(result).isEqualTo(listOf(ValidationMessage(FTR_TYPE_14_DAYS_BUT_LENGTH_IS_28)))
      }

      @Test
      fun `Test 28 day FTR sentence type and 14 day recall`() {
        val result = validationService.validateBeforeCalculation(
          VALID_FTR_SOURCE_DATA.copy(
            sentenceAndOffences = listOf(FTR_28_DAY_SENTENCE).map {
              SentenceAndOffenceWithReleaseArrangements(
                source = it,
                isSdsPlus = false,
                isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
                isSDSPlusOffenceInPeriod = false,
                hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
              )
            },
            fixedTermRecallDetails = FTR_DETAILS_14,
            returnToCustodyDate = ReturnToCustodyDate(BOOKING_ID, FTR_DETAILS_14.returnToCustodyDate),
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

    @Test
    fun `Test no sentences provided returns NO_SENTENCES validation message`() {
      val sourceData = CalculationSourceData(
        sentenceAndOffences = emptyList(),
        prisonerDetails = VALID_PRISONER,
        bookingAndSentenceAdjustments = VALID_ADJUSTMENTS,
        returnToCustodyDate = null,
      )

      val result = validationService.validateBeforeCalculation(
        sourceData,
        USER_INPUTS,
      )

      assertThat(result).containsExactly(ValidationMessage(ValidationCode.NO_SENTENCES))
    }

    @Test
    fun `Test single sentence does not return NO_SENTENCES validation message`() {
      val sourceData =
        CalculationSourceData(
          sentenceAndOffences = listOf(
            SentenceAndOffenceWithReleaseArrangements(
              validSdsSentence,
              false,
              false,
              false,
              SDSEarlyReleaseExclusionType.NO,
            ),
          ),
          prisonerDetails = VALID_PRISONER,
          bookingAndSentenceAdjustments = VALID_ADJUSTMENTS,
          returnToCustodyDate = null,
        )

      val result = validationService.validateBeforeCalculation(
        sourceData,
        USER_INPUTS,
      )

      assertThat(result).isEmpty()
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
        val workingBooking = BOOKING.copy(
          fixedTermRecallDetails = FTR_DETAILS_14,
          sentences = listOf(
            consecutiveSentenceOne,
            consecutiveSentenceTwo,
          ),
        )

        val sentences = BookingHelperTest().createConsecutiveSentences(workingBooking)
        whenever(sentences[0].sentenceCalculation.adjustedExpiryDate).thenReturn(FTR_DETAILS_14.returnToCustodyDate.plusDays(1))
        val result = validationService.validateBookingAfterCalculation(
          CalculationOutput(sentences, emptyList(), mock()),
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
        val workingBooking = BOOKING.copy(
          fixedTermRecallDetails = FTR_DETAILS_28,
          sentences = listOf(
            consecutiveSentenceOne,
            consecutiveSentenceTwo,
          ),
        )
        val sentences = BookingHelperTest().createConsecutiveSentences(workingBooking)
        whenever(sentences[0].sentenceCalculation.adjustedExpiryDate).thenReturn(FTR_DETAILS_14.returnToCustodyDate.plusDays(1))
        val result = validationService.validateBookingAfterCalculation(
          CalculationOutput(sentences, emptyList(), mock()),
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
        val workingBooking = BOOKING.copy(
          fixedTermRecallDetails = FTR_DETAILS_28,
          sentences = listOf(
            consecutiveSentenceOne,
            consecutiveSentenceTwo,
          ),
        )
        val sentences = BookingHelperTest().createConsecutiveSentences(workingBooking)
        whenever(sentences[0].sentenceCalculation.adjustedExpiryDate).thenReturn(FTR_DETAILS_14.returnToCustodyDate.plusDays(1))
        val result = validationService.validateBookingAfterCalculation(
          CalculationOutput(sentences, emptyList(), mock()),
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
        val workingBooking = BOOKING.copy(
          fixedTermRecallDetails = FTR_DETAILS_28,
          sentences = listOf(
            consecutiveSentenceOne,
            consecutiveSentenceTwo,
          ),
        )
        val sentences = BookingHelperTest().createConsecutiveSentences(workingBooking)
        whenever(sentences[0].sentenceCalculation.adjustedExpiryDate).thenReturn(FTR_DETAILS_14.returnToCustodyDate.plusDays(1))
        val result = validationService.validateBookingAfterCalculation(
          CalculationOutput(sentences, emptyList(), mock()),
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
          CalculationSourceData(
            sentenceAndOffences = listOf(sentenceAndOffences).map {
              SentenceAndOffenceWithReleaseArrangements(
                source = it,
                isSdsPlus = false,
                isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
                isSDSPlusOffenceInPeriod = false,
                hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
              )
            },
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = VALID_ADJUSTMENTS,
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )
        assertThat(result).containsExactly(ValidationMessage(ValidationCode.UNSUPPORTED_DTO_RECALL_SEC104_SEC105))
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
          CalculationSourceData(
            sentenceAndOffences = listOf(sentenceAndOffences).map {
              SentenceAndOffenceWithReleaseArrangements(
                source = it,
                isSdsPlus = false,
                isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
                isSDSPlusOffenceInPeriod = false,
                hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
              )
            },
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = VALID_ADJUSTMENTS,
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )
        assertThat(result).containsExactly(ValidationMessage(ValidationCode.UNSUPPORTED_DTO_RECALL_SEC104_SEC105))
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
          CalculationSourceData(
            sentenceAndOffences = listOf(sentenceAndOffences).map {
              SentenceAndOffenceWithReleaseArrangements(
                source = it,
                isSdsPlus = false,
                isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
                isSDSPlusOffenceInPeriod = false,
                hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
              )
            },
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = VALID_ADJUSTMENTS,
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
          CalculationSourceData(
            sentenceAndOffences = listOf(sentenceAndOffences).map {
              SentenceAndOffenceWithReleaseArrangements(
                source = it,
                isSdsPlus = false,
                isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
                isSDSPlusOffenceInPeriod = false,
                hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
              )
            },
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = VALID_ADJUSTMENTS,
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )
        assertThat(result).containsExactly(ValidationMessage(ValidationCode.UNSUPPORTED_DTO_RECALL_SEC104_SEC105))
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
          CalculationSourceData(
            sentenceAndOffences = listOf(sentenceAndOffences).map {
              SentenceAndOffenceWithReleaseArrangements(
                source = it,
                isSdsPlus = false,
                isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
                isSDSPlusOffenceInPeriod = false,
                hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
              )
            },
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = VALID_ADJUSTMENTS,
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )
        assertThat(result).containsExactly(ValidationMessage(ValidationCode.UNSUPPORTED_DTO_RECALL_SEC104_SEC105))
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
          CalculationSourceData(
            sentenceAndOffences = listOf(sentenceAndOffences).map {
              SentenceAndOffenceWithReleaseArrangements(
                source = it,
                isSdsPlus = false,
                isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
                isSDSPlusOffenceInPeriod = false,
                hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
              )
            },
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = VALID_ADJUSTMENTS,
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
          CalculationSourceData(
            sentenceAndOffences = listOf(sentenceAndOffences).map {
              SentenceAndOffenceWithReleaseArrangements(
                source = it,
                isSdsPlus = false,
                isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
                isSDSPlusOffenceInPeriod = false,
                hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
              )
            },
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = VALID_ADJUSTMENTS,
            returnToCustodyDate = null,
          ),
          USER_INPUTS,
        )
        assertThat(result).doesNotContain(ValidationMessage(ValidationCode.UNSUPPORTED_DTO_RECALL_SEC104_SEC105))
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
          CalculationSourceData(
            sentenceAndOffences = listOf(
              SentenceAndOffenceWithReleaseArrangements(
                source = validSdsSentence,
                isSdsPlus = false,
                isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
                isSDSPlusOffenceInPeriod = false,
                hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
              ),
            ),
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = AdjustmentsSourceData(prisonApiData = adjustment),
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
          sourceData = CalculationSourceData(
            sentenceAndOffences = listOf(
              SentenceAndOffenceWithReleaseArrangements(
                source = validSdsSentence,
                isSdsPlus = false,
                isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
                isSDSPlusOffenceInPeriod = false,
                hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
              ),
            ),
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = AdjustmentsSourceData(prisonApiData = adjustment),
            returnToCustodyDate = null,
          ),
          calculationUserInputs = USER_INPUTS,
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
          CalculationSourceData(
            sentenceAndOffences = listOf(
              SentenceAndOffenceWithReleaseArrangements(
                source = validSdsSentence,
                isSdsPlus = false,
                isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
                isSDSPlusOffenceInPeriod = false,
                hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
              ),
            ),
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = AdjustmentsSourceData(prisonApiData = adjustment),
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
          CalculationSourceData(
            sentenceAndOffences = listOf(
              SentenceAndOffenceWithReleaseArrangements(
                source = validSdsSentence,
                isSdsPlus = false,
                isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
                isSDSPlusOffenceInPeriod = false,
                hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
              ),
            ),
            prisonerDetails = VALID_PRISONER,
            bookingAndSentenceAdjustments = AdjustmentsSourceData(prisonApiData = adjustment),
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
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
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
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )

    val result = validationService.validateBeforeCalculation(
      CalculationSourceData(
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
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
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
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
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
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )

    val result = validationService.validateBeforeCalculation(
      CalculationSourceData(
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
  fun `Validate that SDS+ sentences are supported for before SDS early release is implemented`() {
    val validationService = getActiveValidationService(
      SentencesExtractionService(),
      TRANCHE_CONFIGURATION,
    )

    val result = validationService.validateBeforeCalculation(
      CalculationSourceData(
        listOf(validSdsSentence).map {
          SentenceAndOffenceWithReleaseArrangements(
            source = it,
            isSdsPlus = true,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          )
        },
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
  fun `Test LR_ORA with CRD before tranche commencement returns no error`() {
    val validationService = getActiveValidationService(
      SentencesExtractionService(),
      TRANCHE_CONFIGURATION,
    )

    val lrOraSentence = LR_ORA.copy()
    lrOraSentence.sentenceCalculation = mock()
    whenever(lrOraSentence.sentenceCalculation.adjustedDeterminateReleaseDate).thenReturn(LocalDate.of(2024, 1, 1))
    whenever(lrOraSentence.sentenceCalculation.releaseDate).thenReturn(LocalDate.of(2024, 1, 1))
    whenever(lrOraSentence.sentenceCalculation.adjustedHistoricDeterminateReleaseDate).thenReturn(TRANCHE_CONFIGURATION.trancheOneCommencementDate.minusDays(1))

    val booking = BOOKING.copy(
      sentences = listOf(
        lrOraSentence,
      ),
      adjustments = Adjustments(),
    )
    lrOraSentence.releaseDateTypes = ReleaseDateTypes(listOf(ReleaseDateType.TUSED), lrOraSentence, booking.offender)
    val calculationOutput = CalculationOutput(
      listOf(lrOraSentence),
      listOf(),
      mock(),
    )
    val result = validationService.validateBookingAfterCalculation(
      calculationOutput,
      booking,
    )

    assertThat(result).isEmpty()
  }

  @Test
  fun `Tranche 2 Prisoner with consecutive SDS on T1 commencement date returns error`() {
    val testIdentifierUUID = UUID.randomUUID()

    val standardSentenceOne = STANDARD_SENTENCE.copy(
      identifier = testIdentifierUUID,
    )
    val standardSentenceTwo = STANDARD_SENTENCE.copy(
      consecutiveSentenceUUIDs = listOf(testIdentifierUUID),
      sentencedAt = TRANCHE_CONFIGURATION.trancheOneCommencementDate,
    )

    val workingBooking = BOOKING.copy(
      sentences = listOf(
        standardSentenceOne,
        standardSentenceTwo,
      ),
      adjustments = Adjustments(),
    )

    val sentences = BookingHelperTest().createConsecutiveSentences(workingBooking)
    val result = validationService.validateBookingAfterCalculation(
      CalculationOutput(
        sentences,
        emptyList(),
        CalculationResult(
          emptyMap(),
          emptyMap(),
          emptyMap(),
          Period.of(6, 0, 0),
          sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_2,
        ),
      ),
      workingBooking,
    )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(UNSUPPORTED_SDS40_CONSECUTIVE_SDS_BETWEEN_TRANCHE_COMMENCEMENTS),
      ),
    )
  }

  @Test
  fun `Tranche 2 Prisoner with consecutive SDS the day before T2 commencement date returns error`() {
    val testIdentifierUUID = UUID.randomUUID()

    val standardSentenceOne = STANDARD_SENTENCE.copy(
      identifier = testIdentifierUUID,
    )
    val standardSentenceTwo = STANDARD_SENTENCE.copy(
      consecutiveSentenceUUIDs = listOf(testIdentifierUUID),
      sentencedAt = TRANCHE_CONFIGURATION.trancheTwoCommencementDate.minusDays(1),
    )

    val workingBooking = BOOKING.copy(
      sentences = listOf(
        standardSentenceOne,
        standardSentenceTwo,
      ),
      adjustments = Adjustments(),
    )

    val sentences = BookingHelperTest().createConsecutiveSentences(workingBooking)
    val result = validationService.validateBookingAfterCalculation(
      CalculationOutput(
        sentences,
        emptyList(),
        CalculationResult(
          emptyMap(),
          emptyMap(),
          emptyMap(),
          Period.of(6, 0, 0),
          sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_2,
        ),
      ),
      workingBooking,
    )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(UNSUPPORTED_SDS40_CONSECUTIVE_SDS_BETWEEN_TRANCHE_COMMENCEMENTS),
      ),
    )
  }

  @Test
  fun `Tranche 2 Prisoner with consecutive SDS+ sentenced on T1 commencement date returns NO error`() {
    val testIdentifierUUID = UUID.randomUUID()

    val standardSentenceOne = STANDARD_SENTENCE.copy(
      identifier = testIdentifierUUID,
    )
    val consecSdsPlusSentence = STANDARD_SENTENCE.copy(
      consecutiveSentenceUUIDs = listOf(testIdentifierUUID),
      sentencedAt = TRANCHE_CONFIGURATION.trancheOneCommencementDate,
      isSDSPlus = true,
    )

    val workingBooking = BOOKING.copy(
      sentences = listOf(
        standardSentenceOne,
        consecSdsPlusSentence,
      ),
      adjustments = Adjustments(),
    )

    val sentences = BookingHelperTest().createConsecutiveSentences(workingBooking)
    val result = validationService.validateBookingAfterCalculation(
      CalculationOutput(
        sentences,
        emptyList(),
        CalculationResult(
          emptyMap(),
          emptyMap(),
          emptyMap(),
          Period.of(6, 0, 0),
          sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_2,
        ),
      ),
      workingBooking,

    )
    assertThat(result).isEmpty()
  }

  @Test
  fun `Tranche 2 Prisoner with consecutive SDS sentenced on T2 commencement dates returns NO error`() {
    val testIdentifierUUID = UUID.randomUUID()

    val standardSentenceOne = STANDARD_SENTENCE.copy(
      identifier = testIdentifierUUID,
    )
    val standardSentenceTwo = STANDARD_SENTENCE.copy(
      consecutiveSentenceUUIDs = listOf(testIdentifierUUID),
      sentencedAt = TRANCHE_CONFIGURATION.trancheTwoCommencementDate,
    )

    val workingBooking = BOOKING.copy(
      sentences = listOf(
        standardSentenceOne,
        standardSentenceTwo,
      ),
      adjustments = Adjustments(),
    )
    val sentences = BookingHelperTest().createConsecutiveSentences(workingBooking)
    val result = validationService.validateBookingAfterCalculation(
      CalculationOutput(
        sentences,
        emptyList(),
        CalculationResult(
          emptyMap(),
          emptyMap(),
          emptyMap(),
          Period.of(6, 0, 0),
          sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_2,
        ),
      ),
      workingBooking,

    )
    assertThat(result).isEmpty()
  }

  @Test
  fun `Tranche 1 Prisoner with consecutive SDS on T1 commencement date returns NO error`() {
    val testIdentifierUUID = UUID.randomUUID()

    val standardSentenceOne = STANDARD_SENTENCE.copy(
      identifier = testIdentifierUUID,
    )
    val standardSentenceTwo = STANDARD_SENTENCE.copy(
      consecutiveSentenceUUIDs = listOf(testIdentifierUUID),
      sentencedAt = TRANCHE_CONFIGURATION.trancheOneCommencementDate,
    )

    val workingBooking = BOOKING.copy(
      sentences = listOf(
        standardSentenceOne,
        standardSentenceTwo,
      ),
      adjustments = Adjustments(),
    )
    val sentences = BookingHelperTest().createConsecutiveSentences(workingBooking)
    val result = validationService.validateBookingAfterCalculation(
      CalculationOutput(
        sentences,
        emptyList(),
        CalculationResult(
          emptyMap(),
          emptyMap(),
          emptyMap(),
          Period.of(6, 0, 0),
          sdsEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_1,
        ),
      ),
      workingBooking,
    )
    assertThat(result).isEmpty()
  }

  @Test
  fun `Sentence contains SE20 offence with start date 2020-11-30`() {
    val validationService = getActiveValidationService(
      SentencesExtractionService(),
      TRANCHE_CONFIGURATION,
    )

    val sentence1 = (
      SentenceAndOffenceWithReleaseArrangements(
        source = validSdsSentence.copy(
          sentenceDate = LocalDate.of(2023, 8, 8),
          lineSequence = 1,
          caseSequence = 1,
          offence = validSdsSentence.offence.copy(
            offenceCode = "SE20005",
            offenceStartDate = LocalDate.of(2020, 11, 30),
          ),
        ),
        isSdsPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSDSPlusOffenceInPeriod = false,
        hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      )

    val sentence2 = (
      SentenceAndOffenceWithReleaseArrangements(
        source = validSdsSentence.copy(
          sentenceDate = LocalDate.of(2024, 11, 3),
          lineSequence = 1,
          caseSequence = 2,
          offence = validSdsSentence.offence.copy(
            offenceCode = "TH68001",
            offenceStartDate = LocalDate.of(2024, 11, 2),
          ),
        ),
        isSdsPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSDSPlusOffenceInPeriod = false,
        hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      )

    val result = validationService.validateBeforeCalculation(
      CalculationSourceData(
        listOf(sentence1, sentence2),
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(),
        null,
      ),
      USER_INPUTS,
    )
    assertThat(result).containsExactly(ValidationMessage(ValidationCode.SE2020_INVALID_OFFENCE_DETAIL, listOf("SE20005")))
  }

  @Test
  fun `Sentence contains SE20 offence with start date 2024-11-02`() {
    val validationService = getActiveValidationService(
      SentencesExtractionService(),
      TRANCHE_CONFIGURATION,
    )

    val sentence1 = (
      SentenceAndOffenceWithReleaseArrangements(
        source = validSdsSentence.copy(
          sentenceDate = LocalDate.of(2023, 6, 7),
          lineSequence = 1,
          caseSequence = 1,
          sentenceSequence = 1,
          offence = validSdsSentence.offence.copy(
            offenceCode = "CD71005A",
            offenceStartDate = LocalDate.of(2023, 5, 24),
          ),
        ),
        isSdsPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSDSPlusOffenceInPeriod = false,
        hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      )

    val sentence2 = (
      SentenceAndOffenceWithReleaseArrangements(
        source = validSdsSentence.copy(
          sentenceDate = LocalDate.of(2023, 6, 7),
          lineSequence = 1,
          caseSequence = 2,
          consecutiveToSequence = 1,
          offence = validSdsSentence.offence.copy(
            offenceCode = "SE20503",
            offenceStartDate = LocalDate.of(2020, 11, 29),
          ),
        ),
        isSdsPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSDSPlusOffenceInPeriod = false,
        hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      )

    val result = validationService.validateBeforeCalculation(
      CalculationSourceData(
        listOf(sentence1, sentence2),
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(),
        null,
      ),
      USER_INPUTS,
    )
    assertThat(result).containsExactly(ValidationMessage(ValidationCode.SE2020_INVALID_OFFENCE_DETAIL, listOf("SE20503")))
  }

  @Test
  fun `Sentence contains SE20 offence with start date 2020-11-28`() {
    val validationService = getActiveValidationService(
      SentencesExtractionService(),
      TRANCHE_CONFIGURATION,
    )

    val sentence1 = (
      SentenceAndOffenceWithReleaseArrangements(
        source = validSdsSentence.copy(
          sentenceDate = LocalDate.of(2024, 9, 10),
          lineSequence = 1,
          caseSequence = 1,
          sentenceSequence = 1,
          offence = validSdsSentence.offence.copy(
            offenceCode = "SE20012A",
            offenceStartDate = LocalDate.of(2020, 11, 28),
          ),
        ),
        isSdsPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSDSPlusOffenceInPeriod = false,
        hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      )

    val result = validationService.validateBeforeCalculation(
      CalculationSourceData(
        listOf(sentence1),
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(),
        null,
      ),
      USER_INPUTS,
    )
    assertThat(result).containsExactly(ValidationMessage(ValidationCode.SE2020_INVALID_OFFENCE_DETAIL, listOf("SE20012A")))
  }

  @Test
  fun `Sentence contains SE20 offence with start date 2018-07-09`() {
    val validationService = getActiveValidationService(
      SentencesExtractionService(),
      TRANCHE_CONFIGURATION,
    )

    val sentence1 = (
      SentenceAndOffenceWithReleaseArrangements(
        source = validSdsSentence.copy(
          sentenceDate = LocalDate.of(2021, 7, 10),
          lineSequence = 1,
          caseSequence = 1,
          offence = validSdsSentence.offence.copy(
            offenceCode = "SE20574",
            offenceStartDate = LocalDate.of(2018, 7, 9),
          ),
        ),
        isSdsPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSDSPlusOffenceInPeriod = false,
        hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      )

    val sentence2 = (
      SentenceAndOffenceWithReleaseArrangements(
        source = validSdsSentence.copy(
          sentenceDate = LocalDate.of(2022, 6, 6),
          lineSequence = 1,
          caseSequence = 2,
          offence = validSdsSentence.offence.copy(
            offenceCode = "CW13010",
            offenceStartDate = LocalDate.of(2022, 6, 5),
          ),
        ),
        isSdsPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSDSPlusOffenceInPeriod = false,
        hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      )

    val result = validationService.validateBeforeCalculation(
      CalculationSourceData(
        listOf(sentence1, sentence2),
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(),
        null,
      ),
      USER_INPUTS,
    )
    assertThat(result).containsExactly(ValidationMessage(ValidationCode.SE2020_INVALID_OFFENCE_DETAIL, listOf("SE20574")))
  }

  @Test
  fun `Sentence contains two SE20 offence violations dated 2020-11-03 and one valid`() {
    val validationService = getActiveValidationService(
      SentencesExtractionService(),
      TRANCHE_CONFIGURATION,
    )

    val sentence1 = (
      SentenceAndOffenceWithReleaseArrangements(
        source = validSdsSentence.copy(
          sentenceDate = LocalDate.of(2022, 7, 10),
          lineSequence = 1,
          caseSequence = 1,
          sentenceSequence = 1,
          offence = validSdsSentence.offence.copy(
            offenceCode = "SE20015",
            offenceStartDate = LocalDate.of(2020, 11, 3),
          ),
        ),
        isSdsPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSDSPlusOffenceInPeriod = false,
        hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      )

    val sentence2 = (
      SentenceAndOffenceWithReleaseArrangements(
        validSdsSentence.copy(
          sentenceDate = LocalDate.of(2022, 7, 10),
          lineSequence = 1,
          caseSequence = 2,
          sentenceSequence = 2,
          consecutiveToSequence = 1,
          offence = validSdsSentence.offence.copy(
            offenceCode = "SE20016",
            offenceStartDate = LocalDate.of(2020, 11, 3),
          ),
        ),
        isSdsPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSDSPlusOffenceInPeriod = false,
        hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      )

    val sentence3 = (
      SentenceAndOffenceWithReleaseArrangements(
        validSdsSentence.copy(
          sentenceDate = LocalDate.of(2022, 7, 10),
          lineSequence = 1,
          caseSequence = 3,
          sentenceSequence = 3,
          consecutiveToSequence = 2,
          offence = validSdsSentence.offence.copy(
            offenceCode = "SE20502",
            offenceStartDate = LocalDate.of(2020, 12, 4),
          ),
        ),
        isSdsPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSDSPlusOffenceInPeriod = false,
        hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      )

    val result = validationService.validateBeforeCalculation(
      CalculationSourceData(
        listOf(sentence1, sentence2, sentence3),
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(),
        null,
      ),
      USER_INPUTS,
    )

    assertThat(result).containsExactly(
      ValidationMessage(ValidationCode.SE2020_INVALID_OFFENCE_COURT_DETAIL, listOf("1", "1")),
      ValidationMessage(ValidationCode.SE2020_INVALID_OFFENCE_COURT_DETAIL, listOf("2", "1")),
    )
  }

  @Test
  fun `Sentence contains two SE20 offence violations dated 2020-10-23, 2020-11-15 and one valid`() {
    val validationService = getActiveValidationService(
      SentencesExtractionService(),
      TRANCHE_CONFIGURATION,
    )

    val sentence1 = (
      SentenceAndOffenceWithReleaseArrangements(
        source = validSdsSentence.copy(
          sentenceDate = LocalDate.of(2023, 4, 14),
          lineSequence = 1,
          caseSequence = 1,
          offence = validSdsSentence.offence.copy(
            offenceCode = "SE20529",
            offenceStartDate = LocalDate.of(2020, 10, 23),
          ),
        ),
        isSdsPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSDSPlusOffenceInPeriod = false,
        hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      )

    val sentence2 = (
      SentenceAndOffenceWithReleaseArrangements(
        source = validSdsSentence.copy(
          sentenceDate = LocalDate.of(2023, 7, 16),
          lineSequence = 1,
          caseSequence = 2,
          offence = validSdsSentence.offence.copy(
            offenceCode = "SE20535",
            offenceStartDate = LocalDate.of(2020, 11, 15),
          ),
        ),
        isSdsPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSDSPlusOffenceInPeriod = false,
        hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      )

    val sentence3 = (
      SentenceAndOffenceWithReleaseArrangements(
        source = validSdsSentence.copy(
          sentenceDate = LocalDate.of(2023, 8, 18),
          lineSequence = 1,
          caseSequence = 3,
          offence = validSdsSentence.offence.copy(
            offenceCode = "CJ88149",
            offenceStartDate = LocalDate.of(2021, 12, 2),
          ),
        ),
        isSdsPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSDSPlusOffenceInPeriod = false,
        hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      )

    val result = validationService.validateBeforeCalculation(
      CalculationSourceData(
        listOf(sentence1, sentence2, sentence3),
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(),
        null,
      ),
      USER_INPUTS,
    )

    assertThat(result).containsExactly(
      ValidationMessage(ValidationCode.SE2020_INVALID_OFFENCE_COURT_DETAIL, listOf("1", "1")),
      ValidationMessage(ValidationCode.SE2020_INVALID_OFFENCE_COURT_DETAIL, listOf("2", "1")),
    )
  }

  @Test
  fun `Sentence contains no SE20 offence violations with offence dated 2024-03-08`() {
    val validationService = getActiveValidationService(
      SentencesExtractionService(),
      TRANCHE_CONFIGURATION,
    )

    val sentence1 = (
      SentenceAndOffenceWithReleaseArrangements(
        source = validSdsSentence.copy(
          sentenceDate = LocalDate.of(2024, 3, 9),
          lineSequence = 1,
          caseSequence = 1,
          offence = validSdsSentence.offence.copy(
            offenceCode = "SE20005",
            offenceStartDate = LocalDate.of(2024, 3, 8),
          ),
        ),
        isSdsPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSDSPlusOffenceInPeriod = false,
        hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      )

    val result = validationService.validateBeforeCalculation(
      CalculationSourceData(
        listOf(sentence1),
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
  fun `Sentence contains no SE20 offence violations with offence dated 2020-12-24`() {
    val validationService = getActiveValidationService(
      SentencesExtractionService(),
      TRANCHE_CONFIGURATION,
    )

    val sentence1 = (
      SentenceAndOffenceWithReleaseArrangements(
        source = validSdsSentence.copy(
          sentenceDate = LocalDate.of(2024, 2, 2),
          lineSequence = 1,
          caseSequence = 1,
          offence = validSdsSentence.offence.copy(
            offenceCode = "SEC250",
            offenceStartDate = LocalDate.of(2020, 12, 24),
          ),
        ),
        isSdsPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSDSPlusOffenceInPeriod = false,
        hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
      )
      )

    val result = validationService.validateBeforeCalculation(
      CalculationSourceData(
        listOf(sentence1),
        VALID_PRISONER,
        VALID_ADJUSTMENTS,
        listOf(),
        null,
      ),
      USER_INPUTS,
    )
    assertThat(result).isEmpty()
  }

  companion object {
    val FIRST_MAY_2018: LocalDate = LocalDate.of(2018, 5, 1)
    val FIRST_MAY_2021: LocalDate = LocalDate.of(2021, 5, 1)
    private const val LINE_SEQ = 2
    private const val CASE_SEQ = 1
    val ONE_MONTH_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 1L, YEARS to 0L))
    val FIVE_YEAR_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L))
    val NINE_MONTH_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 9L, YEARS to 0L))
    val FIRST_JAN_2015: LocalDate = LocalDate.of(2015, 1, 1)
    val DOB: LocalDate = LocalDate.of(1980, 1, 1)
    val TRANCHE_CONFIGURATION = SDS40TrancheConfiguration(
      LocalDate.of(2024, 9, 10),
      LocalDate.of(2024, 10, 22),
      LocalDate.of(2024, 12, 16),
    )

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
    private val VALID_ADJUSTMENTS = AdjustmentsSourceData(prisonApiData = BookingAndSentenceAdjustments(emptyList(), emptyList()))
    private const val BOOKING_ID = 100091L
    private val RETURN_TO_CUSTODY_DATE = LocalDate.of(2022, 3, 15)
    private val FTR_DETAILS_14 = FixedTermRecallDetails(BOOKING_ID, RETURN_TO_CUSTODY_DATE, 14)
    private val FTR_DETAILS_28 = FixedTermRecallDetails(BOOKING_ID, RETURN_TO_CUSTODY_DATE, 28)
    private val FTR_DETAILS_NO_RTC = mock(FixedTermRecallDetails::class.java)
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
    private val VALID_FTR_SOURCE_DATA = CalculationSourceData(
      sentenceAndOffences = listOf(FTR_14_DAY_SENTENCE).map {
        SentenceAndOffenceWithReleaseArrangements(
          source = it,
          isSdsPlus = false,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
          isSDSPlusOffenceInPeriod = false,
          hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
        )
      },
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

    private val LR_ORA = StandardDeterminateSentence(
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
    val STANDARD_SENTENCE = StandardDeterminateSentence(
      OFFENCE,
      ONE_DAY_DURATION,
      LocalDate.of(2020, 1, 1),
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    val SENTENCE_CALCULATION = mock<SentenceCalculation>()

    val BOOKING = Booking(
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

  private fun getActiveValidationService(
    sentencesExtractionService: SentencesExtractionService,
    trancheConfiguration: SDS40TrancheConfiguration,
  ): ValidationService {
    val featureToggles = FeatureToggles()
    val validationUtilities = ValidationUtilities()
    val fineValidationService = FineValidationService(validationUtilities)
    val adjustmentValidationService = AdjustmentValidationService()
    val dtoValidationService = DtoValidationService()
    val botusValidationService = BotusValidationService(featureToggles)
    val recallValidationService = RecallValidationService(trancheConfiguration, validationUtilities, featureToggles)
    val unsupportedValidationService = UnsupportedValidationService()
    val postCalculationValidationService = PostCalculationValidationService(trancheConfiguration)
    val section91ValidationService = Section91ValidationService(validationUtilities)
    val sopcValidationService = SOPCValidationService(validationUtilities)
    val edsValidationService = EDSValidationService(validationUtilities)
    val manageOffencesService = mock<ManageOffencesService>()
    val toreraValidationService = ToreraValidationService(manageOffencesService)
    val dateValidationService = DateValidationService()
    val sentenceValidationService = SentenceValidationService(
      validationUtilities,
      sentencesExtractionService,
      section91ValidationService = section91ValidationService,
      sopcValidationService = sopcValidationService,
      fineValidationService,
      edsValidationService = edsValidationService,
      featuresToggles = featureToggles,
    )
    val preCalculationValidationService = PreCalculationValidationService(
      featureToggles = featureToggles,
      fineValidationService = fineValidationService,
      adjustmentValidationService = adjustmentValidationService,
      dtoValidationService = dtoValidationService,
      botusValidationService = botusValidationService,
      unsupportedValidationService = unsupportedValidationService,
      toreraValidationService = toreraValidationService,
    )

    return ValidationService(
      preCalculationValidationService = preCalculationValidationService,
      adjustmentValidationService = adjustmentValidationService,
      recallValidationService = recallValidationService,
      sentenceValidationService = sentenceValidationService,
      validationUtilities = validationUtilities,
      postCalculationValidationService = postCalculationValidationService,
      dateValidationService = dateValidationService,
    )
  }
}
