package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheOneDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheThreeDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheTwoDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Recall
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.FTR_14_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.FTR_48_COMMENCEMENT_DATE
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.*

@ExtendWith(MockitoExtension::class)
class RecallValidationServiceTest {

  private val recallValidationService = RecallValidationService(
    trancheConfiguration = SDS40TrancheConfiguration(
      trancheOneCommencementDate = sdsEarlyReleaseTrancheOneDate(),
      trancheTwoCommencementDate = sdsEarlyReleaseTrancheTwoDate(),
      trancheThreeCommencementDate = sdsEarlyReleaseTrancheThreeDate(),
    ),
    validationUtilities = ValidationUtilities(),
    featureToggles = FeatureToggles(ftr48ManualJourney = true, extraReturnToCustodyValidation = true),
  )

  @Nested
  inner class ValidateReturnToCustodyDateTests {

    @Test
    fun `Validate return to custody date passed`() {
      val sentence = FTR_14_DAY_SENTENCE

      val messages = recallValidationService.validateFixedTermRecall(createSourceData(listOf(sentence), LocalDate.of(2024, 2, 1), 14))

      assertThat(messages).isEmpty()
    }

    @Test
    fun `Validate return to custody before sentence date fails`() {
      val sentence = FTR_14_DAY_SENTENCE

      val messages = recallValidationService.validateFixedTermRecall(createSourceData(listOf(sentence), sentence.sentenceDate.minusDays(1), 14))

      assertThat(messages).isNotEmpty
      assertThat(messages[0].code).isEqualTo(ValidationCode.FTR_RTC_DATE_BEFORE_SENTENCE_DATE)
    }

    @Test
    fun `Validate return to custody in future fails`() {
      val sentence = FTR_14_DAY_SENTENCE

      val messages = recallValidationService.validateFixedTermRecall(createSourceData(listOf(sentence), LocalDate.now().plusDays(1), 14))

      assertThat(messages).isNotEmpty
      assertThat(messages[0].code).isEqualTo(ValidationCode.FTR_RTC_DATE_IN_FUTURE)
    }

    @Test
    fun `Validate return to custody before revocation for FTR-56 fails`() {
      val sentence = FTR_14_DAY_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.FTR_56ORA.name)

      val messages = recallValidationService.validateFixedTermRecall(createSourceData(listOf(sentence), LocalDate.of(2023, 12, 1), 14))

      assertThat(messages).isNotEmpty
      assertThat(messages[0].code).isEqualTo(ValidationCode.FTR_RTC_DATE_BEFORE_REVOCATION_DATE)
    }
  }

  @Nested
  inner class ValidateRevocationDateTests {

    @Test
    fun `Validate revocation date passed`() {
      val sentence = FTR_14_DAY_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.FTR_56ORA.name)

      val messages = recallValidationService.validateRevocationDate(createSourceData(listOf(sentence)))

      assertThat(messages).isEmpty()
    }

    @Test
    fun `Validate missing revocation date for non ftr-56 passed`() {
      val sentence = FTR_14_DAY_SENTENCE.copy(revocationDates = emptyList())

      val messages = recallValidationService.validateRevocationDate(createSourceData(listOf(sentence)))

      assertThat(messages).isEmpty()
    }

    @Test
    fun `Validate revocation date missing for FTR-56`() {
      val sentence = FTR_14_DAY_SENTENCE.copy(revocationDates = emptyList(), sentenceCalculationType = SentenceCalculationType.FTR_56ORA.name)

      val messages = recallValidationService.validateRevocationDate(createSourceData(listOf(sentence)))

      assertThat(messages).isNotEmpty
      assertThat(messages[0].code).isEqualTo(ValidationCode.RECALL_MISSING_REVOCATION_DATE)
    }

    @Test
    fun `Validate revocation date in future for all recall sentence types`() {
      val sentence = FTR_14_DAY_SENTENCE.copy(revocationDates = listOf(LocalDate.now().plusDays(1)))

      val messages = recallValidationService.validateRevocationDate(createSourceData(listOf(sentence)))

      assertThat(messages).isNotEmpty
      assertThat(messages[0].code).isEqualTo(ValidationCode.REVOCATION_DATE_IN_THE_FUTURE)
    }

    @Test
    fun `Do not validate revocation date for non ftr-56 recalls`() {
      val sentence = FTR_14_DAY_SENTENCE.copy(revocationDates = emptyList())

      val messages = recallValidationService.validateRevocationDate(createSourceData(listOf(sentence)))

      assertThat(messages).isEmpty()
    }
  }

  @Nested
  @DisplayName("validateFtrFortyOverlap")
  inner class ValidateFtrFortyOverlapTests {
    @Test
    fun `returns validation message when all sentences matches all conditions`() {
      val sentence24Months = createSingleFTRSentence(SENTENCE_DATE_BEFORE_COMMENCEMENT, 24L)
      val calculationOutput = listOf(sentence24Months)

      val result = recallValidationService.validateFtrFortyOverlap(calculationOutput)

      assertEquals(1, result.size)
      assertEquals(ValidationCode.FTR_TYPE_48_DAYS_OVERLAPPING_SENTENCE, result.first().code)
    }

    @Test
    fun `returns validation message when aggregate sentence matches all conditions`() {
      val consecutiveSentence = createConsecutiveFTRSentence(SENTENCE_DATE_BEFORE_COMMENCEMENT, 24L, 23L)

      val calculationOutput = listOf(consecutiveSentence)

      val result = recallValidationService.validateFtrFortyOverlap(calculationOutput)

      assertEquals(1, result.size)
      assertEquals(ValidationCode.FTR_TYPE_48_DAYS_OVERLAPPING_SENTENCE, result.first().code)
    }

    @Test
    fun `returns validation message when aggregate duration is 12 months or more`() {
      val consecutiveSentence = createConsecutiveFTRSentence(SENTENCE_DATE_BEFORE_COMMENCEMENT, 6L, 6L)
      val calculationOutput = listOf(consecutiveSentence)

      val result = recallValidationService.validateFtrFortyOverlap(calculationOutput)

      assertEquals(1, result.size)
      assertEquals(ValidationCode.FTR_TYPE_48_DAYS_OVERLAPPING_SENTENCE, result.first().code)
    }

    @Test
    fun `returns empty list when duration is less than 12 months`() {
      val sentence11Months = createSingleFTRSentence(SENTENCE_DATE_BEFORE_COMMENCEMENT, 11L)
      val calculationOutput = listOf(sentence11Months)

      val result = recallValidationService.validateFtrFortyOverlap(calculationOutput)
      assertThat(result).isEmpty()
    }

    @Test
    fun `returns empty list when aggregate duration is less than 12 months`() {
      val consecutiveSentence = createConsecutiveFTRSentence(SENTENCE_DATE_BEFORE_COMMENCEMENT, 6L, 5L)
      val calculationOutput = listOf(consecutiveSentence)

      val result = recallValidationService.validateFtrFortyOverlap(calculationOutput)

      assertThat(result).isEmpty()
    }

    @Test
    fun `returns empty list when aggregate duration is 48 months or more`() {
      val consecutiveSentence = createConsecutiveFTRSentence(SENTENCE_DATE_BEFORE_COMMENCEMENT, 24L, 24L)
      val calculationOutput = listOf(consecutiveSentence)

      val result = recallValidationService.validateFtrFortyOverlap(calculationOutput)

      assertThat(result).isEmpty()
    }

    @Test
    fun `returns empty list when sentence is after FTR 48 commencement date`() {
      val sentence18Months = createSingleFTRSentence(FTR_48_COMMENCEMENT_DATE.plusDays(1), 18L)
      val calculationOutput = listOf(sentence18Months)

      val result = recallValidationService.validateFtrFortyOverlap(calculationOutput)

      assertThat(result).isEmpty()
    }

    @Test
    fun `returns empty list when sentence is on FTR 48 commencement date`() {
      val sentence18Months = createSingleFTRSentence(FTR_48_COMMENCEMENT_DATE, 18L)
      val calculationOutput = listOf(sentence18Months)

      val result = recallValidationService.validateFtrFortyOverlap(calculationOutput)

      assertThat(result).isEmpty()
    }
  }

  private fun createConsecutiveFTRSentence(
    sentenceDate: LocalDate,
    firstSentenceLengthMonths: Long,
    secondSentenceLengthMonths: Long,
  ): ConsecutiveSentence {
    val consecutiveSentencePartOne = StandardDeterminateSentence(
      sentencedAt = sentenceDate,
      duration = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to firstSentenceLengthMonths, YEARS to 0L)),
      offence = Offence(
        committedAt = sentenceDate,
        offenceCode = "PL96003",
      ),
      identifier = UUID.randomUUID(),
      lineSequence = 1,
      caseSequence = 1,
      recall = Recall(RecallType.FIXED_TERM_RECALL_28),
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    val consecutiveSentencePartTwo = StandardDeterminateSentence(
      sentencedAt = sentenceDate,
      duration = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to secondSentenceLengthMonths, YEARS to 0L)),
      offence = Offence(
        committedAt = sentenceDate,
        offenceCode = "PL96003",
      ),
      identifier = UUID.randomUUID(),
      lineSequence = 2,
      caseSequence = 1,
      recall = Recall(RecallType.FIXED_TERM_RECALL_28),
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
      consecutiveSentenceUUIDs = listOf(consecutiveSentencePartOne.identifier),
    )
    return ConsecutiveSentence(listOf(consecutiveSentencePartOne, consecutiveSentencePartTwo))
  }

  private fun createSingleFTRSentence(sentenceDate: LocalDate, months: Long) = StandardDeterminateSentence(
    sentencedAt = sentenceDate,
    duration = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to months, YEARS to 0L)),
    offence = Offence(
      committedAt = sentenceDate,
      offenceCode = "PL96003",
    ),
    identifier = UUID.randomUUID(),
    lineSequence = 1,
    caseSequence = 1,
    recall = Recall(RecallType.FIXED_TERM_RECALL_28),
    isSDSPlus = false,
    hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
  )

  private fun createSourceData(sentences: List<SentenceAndOffenceWithReleaseArrangements>, returnToCustodyDate: LocalDate? = null, recallLength: Int? = null) = CalculationSourceData(
    prisonerDetails = mock(),
    sentenceAndOffences = sentences,
    bookingAndSentenceAdjustments = mock(),
    returnToCustodyDate = if (returnToCustodyDate != null)ReturnToCustodyDate(returnToCustodyDate = returnToCustodyDate, bookingId = 1L) else null,
    fixedTermRecallDetails = if (recallLength != null && returnToCustodyDate != null) FixedTermRecallDetails(returnToCustodyDate = returnToCustodyDate, bookingId = 1L, recallLength = recallLength) else null,
  )

  private companion object {
    val SENTENCE_DATE_BEFORE_COMMENCEMENT: LocalDate = LocalDate.of(2015, 1, 1)

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
      courtDescription = null,
      courtTypeCode = null,
      consecutiveToSequence = null,
      revocationDates = listOf(LocalDate.of(2024, 1, 1)),
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
  }
}
