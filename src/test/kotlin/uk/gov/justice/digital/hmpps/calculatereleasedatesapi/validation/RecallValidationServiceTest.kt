package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheOneDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheThreeDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheTwoDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
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
    featureToggles = FeatureToggles(ftr48ManualJourney = true),
  )

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

  private fun createConsecutiveFTRSentence(sentenceDate: LocalDate, firstSentenceLengthMonths: Long, secondSentenceLengthMonths: Long): ConsecutiveSentence {
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
      recallType = RecallType.FIXED_TERM_RECALL_28,
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
      recallType = RecallType.FIXED_TERM_RECALL_28,
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
    recallType = RecallType.FIXED_TERM_RECALL_28,
    isSDSPlus = false,
    hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
  )
  private companion object {
    val SENTENCE_DATE_BEFORE_COMMENCEMENT: LocalDate = LocalDate.of(2015, 1, 1)
  }
}
