package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.FTR_14_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonApiExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator.FixedTermRecallValidator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator.RevocationDateValidator
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class RecallValidationServiceTest {

  @Nested
  inner class ValidateReturnToCustodyDateTests {
    val fixedTermRecallValidator = FixedTermRecallValidator()

    @Test
    fun `Validate return to custody date passed`() {
      val sentence = FTR_14_DAY_SENTENCE

      val messages = fixedTermRecallValidator.validate(createSourceData(listOf(sentence), LocalDate.of(2024, 2, 1), 14))

      assertThat(messages).isEmpty()
    }

    @Test
    fun `Validate return to custody before sentence date fails`() {
      val sentence = FTR_14_DAY_SENTENCE

      val messages = fixedTermRecallValidator.validate(createSourceData(listOf(sentence), sentence.sentenceDate.minusDays(1), 14))

      assertThat(messages).isNotEmpty
      assertThat(messages[0].code).isEqualTo(ValidationCode.FTR_RTC_DATE_BEFORE_SENTENCE_DATE)
    }

    @Test
    fun `Validate return to custody in future fails`() {
      val sentence = FTR_14_DAY_SENTENCE

      val messages = fixedTermRecallValidator.validate(createSourceData(listOf(sentence), LocalDate.now().plusDays(1), 14))

      assertThat(messages).isNotEmpty
      assertThat(messages[0].code).isEqualTo(ValidationCode.FTR_RTC_DATE_IN_FUTURE)
    }
  }

  @Nested
  inner class ValidateRevocationDateTests {
    val revocationDateValidator = RevocationDateValidator()

    @Test
    fun `Validate revocation date passed`() {
      val sentence = FTR_14_DAY_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.FTR_56ORA.name)

      val messages = revocationDateValidator.validate(createSourceData(listOf(sentence)))

      assertThat(messages).isEmpty()
    }

    @Test
    fun `Validate missing revocation date for non ftr-56 passed`() {
      val sentence = FTR_14_DAY_SENTENCE.copy(revocationDates = emptyList())

      val messages = revocationDateValidator.validate(createSourceData(listOf(sentence)))

      assertThat(messages).isEmpty()
    }

    @Test
    fun `Validate revocation date missing for FTR-56`() {
      val sentence = FTR_14_DAY_SENTENCE.copy(revocationDates = emptyList(), sentenceCalculationType = SentenceCalculationType.FTR_56ORA.name)

      val messages = revocationDateValidator.validate(createSourceData(listOf(sentence)))

      assertThat(messages).isNotEmpty
      assertThat(messages[0].code).isEqualTo(ValidationCode.RECALL_MISSING_REVOCATION_DATE)
    }

    @Test
    fun `Validate revocation date in future for all recall sentence types`() {
      val sentence = FTR_14_DAY_SENTENCE.copy(revocationDates = listOf(LocalDate.now().plusDays(1)))

      val messages = revocationDateValidator.validate(createSourceData(listOf(sentence)))

      assertThat(messages).isNotEmpty
      assertThat(messages[0].code).isEqualTo(ValidationCode.REVOCATION_DATE_IN_THE_FUTURE)
    }

    @Test
    fun `Do not validate revocation date for non ftr-56 recalls`() {
      val sentence = FTR_14_DAY_SENTENCE.copy(revocationDates = emptyList())

      val messages = revocationDateValidator.validate(createSourceData(listOf(sentence)))

      assertThat(messages).isEmpty()
    }
  }

  @Nested
  @DisplayName("validateFtrDoNotConflict")
  inner class ValidateFtrDoNotConflictTests {
    val fixedTermRecallValidator = FixedTermRecallValidator()

    @Test
    fun `Mixed FTR28 and FTR56 sentences return an error`() {
      val result = fixedTermRecallValidator.validate(
        createSourceData(
          listOf(FTR_28_DAY_SENTENCE, FTR_56_DAY_SENTENCE),
          LocalDate.of(2024, 12, 20),
          20,
          null,
        ),
      )

      assertEquals(ValidationCode.FTR_SENTENCES_CONFLICT_WITH_EACH_OTHER, result.first().code)
    }

    @Test
    fun `Mixed FTR28 and FTR14 sentences return an error`() {
      val result = fixedTermRecallValidator.validate(
        createSourceData(
          listOf(FTR_14_DAY_SENTENCE, FTR_28_DAY_SENTENCE),
          LocalDate.of(2024, 12, 20),
          20,
          null,
        ),
      )

      assertEquals(ValidationCode.FTR_SENTENCES_CONFLICT_WITH_EACH_OTHER, result.first().code)
    }

    @Test
    fun `Mixed FTR14 and FTR56 sentences return an error`() {
      val result = fixedTermRecallValidator.validate(
        createSourceData(
          listOf(FTR_14_DAY_SENTENCE, FTR_56_DAY_SENTENCE),
          LocalDate.of(2024, 12, 20),
          20,
          null,
        ),
      )

      assertEquals(ValidationCode.FTR_SENTENCES_CONFLICT_WITH_EACH_OTHER, result.first().code)
    }

    @Test
    fun `Mixed FTR sentences return an error`() {
      val result = fixedTermRecallValidator.validate(
        createSourceData(
          listOf(FTR_14_DAY_SENTENCE, FTR_28_DAY_SENTENCE, FTR_56_DAY_SENTENCE),
          LocalDate.of(2024, 12, 20),
          20,
          null,
        ),
      )

      assertEquals(ValidationCode.FTR_SENTENCES_CONFLICT_WITH_EACH_OTHER, result.first().code)
    }
  }

  private fun createSourceData(
    sentences: List<SentenceAndOffenceWithReleaseArrangements>,
    returnToCustodyDate: LocalDate? = null,
    recallLength: Int? = null,
    movements: List<PrisonApiExternalMovement>? = null,
  ) = CalculationSourceData(
    prisonerDetails = prisonerDetails,
    sentenceAndOffences = sentences,
    bookingAndSentenceAdjustments = mock(),
    returnToCustodyDate = if (returnToCustodyDate != null) {
      ReturnToCustodyDate(
        returnToCustodyDate = returnToCustodyDate,
        bookingId = 1L,
      )
    } else {
      null
    },
    fixedTermRecallDetails = if (returnToCustodyDate != null && recallLength != null) {
      FixedTermRecallDetails(
        returnToCustodyDate = returnToCustodyDate,
        bookingId = 1L,
        recallLength = recallLength,
      )
    } else {
      null
    },
    movements = movements ?: emptyList(),
  )

  private companion object {
    private val FTR_14_DAY_SENTENCE = SentenceAndOffenceWithReleaseArrangements(
      bookingId = 1L,
      sentenceSequence = 7,
      lineSequence = 1,
      caseSequence = 1,
      sentenceDate = LocalDate.of(2021, 1, 1),
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
      courtId = null,
      courtDescription = null,
      courtTypeCode = null,
      consecutiveToSequence = null,
      revocationDates = listOf(LocalDate.of(2024, 1, 1)),
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )

    private val FTR_56_DAY_SENTENCE = FTR_14_DAY_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.FTR_56ORA.name)

    private val FTR_28_DAY_SENTENCE = FTR_14_DAY_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.FTRSCH15_ORA.name)

    private val prisonerDetails = PrisonerDetails(
      bookingId = 1L,
      offenderNo = "ABC",
      dateOfBirth = LocalDate.of(1980, 1, 1),
    )
  }
}
