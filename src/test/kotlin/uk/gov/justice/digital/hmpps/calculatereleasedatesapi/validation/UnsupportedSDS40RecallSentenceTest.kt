package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.springframework.context.annotation.Profile
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.STANDARD_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateTypes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_SDS40_RECALL_SENTENCE_TYPE
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.UUID

@Profile("tests")
class UnsupportedSDS40RecallSentenceTest {

  @Test
  fun `Test recall sentence issued after trancheOneCommencementDate does not trigger validation`() {
    val dateAfterTrancheOne = TRANCHE_CONFIGURATION.trancheOneCommencementDate.plusDays(1)
    val lrOraSentence = LR_ORA.copy(sentencedAt = dateAfterTrancheOne)

    val workingBooking = booking.copy(
      sentences = listOf(
        lrOraSentence,
      ),
      adjustments = Adjustments(),
    )
    val offender = mock<Offender>()
    whenever(offender.underEighteenAt).thenReturn { false }
    lrOraSentence.releaseDateTypes =
      ReleaseDateTypes(listOf(ReleaseDateType.TUSED), lrOraSentence, offender)
    lrOraSentence.sentenceCalculation = mock()
    whenever(lrOraSentence.sentenceCalculation.adjustedHistoricDeterminateReleaseDate).thenReturn(dateAfterTrancheOne.plusYears(1))

    val calculationOutput = CalculationOutput(
      listOf(lrOraSentence),
      listOf(),
      mock(),
    )

    val result = RecallValidationService(TRANCHE_CONFIGURATION, ValidationUtilities(), FeatureToggles()).validateUnsupportedRecallTypes(
      calculationOutput,
      workingBooking,
    )

    assertThat(result).isEmpty()
  }

  @Test
  fun `Test recall sentence issued before trancheOneCommencementDate does trigger validation`() {
    val dateBeforeTrancheOne = TRANCHE_CONFIGURATION.trancheOneCommencementDate.minusDays(1)
    val dateAfterTrancheOne = TRANCHE_CONFIGURATION.trancheOneCommencementDate.plusDays(1)
    val lrOraSentence = LR_ORA.copy(sentencedAt = dateBeforeTrancheOne)

    val workingBooking = booking.copy(
      sentences = listOf(
        lrOraSentence,
      ),
      adjustments = Adjustments(),
    )
    val offender = mock<Offender>()
    whenever(offender.underEighteenAt).thenReturn { false }
    lrOraSentence.releaseDateTypes =
      ReleaseDateTypes(listOf(ReleaseDateType.TUSED), lrOraSentence, offender)
    lrOraSentence.sentenceCalculation = mock()
    whenever(lrOraSentence.sentenceCalculation.adjustedHistoricDeterminateReleaseDate).thenReturn(dateAfterTrancheOne)

    val calculationOutput = CalculationOutput(
      listOf(lrOraSentence),
      listOf(),
      mock(),
    )

    val result = RecallValidationService(TRANCHE_CONFIGURATION, ValidationUtilities(), FeatureToggles()).validateUnsupportedRecallTypes(
      calculationOutput,
      workingBooking,
    )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(UNSUPPORTED_SDS40_RECALL_SENTENCE_TYPE),
      ),
    )
  }

  @Test
  fun `Test LR with no TUSED after tranche commencement returns no error`() {
    val dateBeforeTrancheOne = TRANCHE_CONFIGURATION.trancheOneCommencementDate.minusDays(1)
    val lrOraSentence = LR_ORA.copy()
    val workingBooking = booking.copy(
      sentences = listOf(
        lrOraSentence,
      ),
      adjustments = Adjustments(),
    )
    val offender = mock<Offender>()
    whenever(offender.underEighteenAt).thenReturn { false }
    lrOraSentence.releaseDateTypes =
      ReleaseDateTypes(listOf(ReleaseDateType.CRD), lrOraSentence, offender)
    lrOraSentence.sentenceCalculation = mock()
    whenever(lrOraSentence.sentenceCalculation.adjustedHistoricDeterminateReleaseDate).thenReturn(dateBeforeTrancheOne)
    val calculationOutput = CalculationOutput(
      listOf(lrOraSentence),
      listOf(),
      mock(),
    )

    val result = RecallValidationService(TRANCHE_CONFIGURATION, ValidationUtilities(), FeatureToggles()).validateUnsupportedRecallTypes(
      calculationOutput,
      workingBooking,
    )

    assertThat(result).isEmpty()
  }

  private val booking = Booking(
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

  private companion object {
    val FIVE_YEAR_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L))
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
  }
}
