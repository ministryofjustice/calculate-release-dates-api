package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheOneDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheThreeDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheTwoDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.FTR_48_COMMENCEMENT_DATE
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
    @Test()
    fun `returns validation message when sentence matches all conditions`() {
      val sentence = mock<AbstractSentence> {
        on { recallType } doReturn RecallType.FIXED_TERM_RECALL_28
        on { sentencedAt } doReturn LocalDate.of(2020, 1, 1)
        on { durationIsGreaterThanOrEqualTo(12, ChronoUnit.MONTHS) } doReturn true
        on { durationIsLessThan(4, ChronoUnit.YEARS) } doReturn true
      }

      val result = recallValidationService.validateFtrFortyOverlap(listOf(sentence))

      assertEquals(1, result.size)
      assertEquals(ValidationCode.FTR_TYPE_48_DAYS_OVERLAPPING_SENTENCE, result.first().code)
    }

    @Test
    fun `returns empty list when duration is less than 12 months`() {
      val sentence = mock<AbstractSentence> {
        on { recallType } doReturn RecallType.FIXED_TERM_RECALL_28
        on { sentencedAt } doReturn LocalDate.of(2020, 1, 1)
        on { durationIsGreaterThanOrEqualTo(12, ChronoUnit.MONTHS) } doReturn false
      }

      val result = recallValidationService.validateFtrFortyOverlap(listOf(sentence))

      assert(result.isEmpty())
    }

    @Test
    fun `returns empty list when duration is 4 years or more`() {
      val sentence = mock<AbstractSentence> {
        on { recallType } doReturn RecallType.FIXED_TERM_RECALL_28
        on { sentencedAt } doReturn LocalDate.of(2020, 1, 1)
        on { durationIsGreaterThanOrEqualTo(12, ChronoUnit.MONTHS) } doReturn true
        on { durationIsLessThan(4, ChronoUnit.YEARS) } doReturn false
      }

      val result = recallValidationService.validateFtrFortyOverlap(listOf(sentence))

      assert(result.isEmpty())
    }

    @Test
    fun `returns empty list when sentence is after FTR 48 commencement date`() {
      val sentence = mock<AbstractSentence> {
        on { recallType } doReturn RecallType.FIXED_TERM_RECALL_28
        on { sentencedAt } doReturn FTR_48_COMMENCEMENT_DATE.plusDays(1)
      }

      val result = recallValidationService.validateFtrFortyOverlap(listOf(sentence))

      assert(result.isEmpty())
    }

    @Test
    fun `returns empty list when sentence is on FTR 48 commencement date`() {
      val sentence = mock<AbstractSentence> {
        on { recallType } doReturn RecallType.FIXED_TERM_RECALL_28
        on { sentencedAt } doReturn FTR_48_COMMENCEMENT_DATE
      }

      val result = recallValidationService.validateFtrFortyOverlap(listOf(sentence))

      assert(result.isEmpty())
    }
  }
}
