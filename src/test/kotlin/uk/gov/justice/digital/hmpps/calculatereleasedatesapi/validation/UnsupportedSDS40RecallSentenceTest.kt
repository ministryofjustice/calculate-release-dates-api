package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.context.annotation.Profile
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.STANDARD_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateTypes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ManageOffencesService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SentencesExtractionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.BookingHelperTest
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
  fun `Test LR_ORA with CRD after tranche commencement returns a validation error`() {
    val validationService = getActiveValidationService(
      SentencesExtractionService(),
      TRANCHE_CONFIGURATION,
    )

    val lrOraSentence = LR_ORA.copy()

    lrOraSentence.sentenceCalculation = SENTENCE_CALCULATION.copy(
      unadjustedHistoricDeterminateReleaseDate = LocalDate.of(2024, 9, 11),
    )
    var workingBooking = booking.copy(
      sentences = listOf(
        lrOraSentence,
      ),
      adjustments = Adjustments(),
    )
    lrOraSentence.releaseDateTypes =
      ReleaseDateTypes(listOf(ReleaseDateType.TUSED), lrOraSentence, workingBooking.offender)

    workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)

    val result = validationService.validateBookingAfterCalculation(
      workingBooking,
    )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(UNSUPPORTED_SDS40_RECALL_SENTENCE_TYPE),
      ),
    )
  }

  @Test
  fun `Test consecutive sentence with LR_ORA with CRD after tranche commencement returns a validation error`() {
    val validationService = getActiveValidationService(
      SentencesExtractionService(),
      TRANCHE_CONFIGURATION,
    )

    val testIdentifierUUID = UUID.randomUUID()

    val lrOraSentence = LR_ORA.copy(
      identifier = testIdentifierUUID,
    )
    val standardSentence = STANDARD_SENTENCE.copy(
      consecutiveSentenceUUIDs = listOf(testIdentifierUUID),
    )

    lrOraSentence.sentenceCalculation = SENTENCE_CALCULATION.copy(
      unadjustedHistoricDeterminateReleaseDate = LocalDate.of(2024, 9, 11),
    )
    var workingBooking = booking.copy(
      sentences = listOf(
        lrOraSentence,
        standardSentence,
      ),
      adjustments = Adjustments(),
    )
    lrOraSentence.releaseDateTypes =
      ReleaseDateTypes(listOf(ReleaseDateType.TUSED), lrOraSentence, workingBooking.offender)

    workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)
    workingBooking.consecutiveSentences[0].sentenceCalculation = SENTENCE_CALCULATION.copy(
      unadjustedHistoricDeterminateReleaseDate = LocalDate.of(2024, 9, 11),
    )
    workingBooking.consecutiveSentences[0].releaseDateTypes =
      ReleaseDateTypes(listOf(ReleaseDateType.TUSED), lrOraSentence, workingBooking.offender)

    val result = validationService.validateBookingAfterCalculation(
      workingBooking,
    )

    assertThat(result).isEqualTo(
      listOf(
        ValidationMessage(UNSUPPORTED_SDS40_RECALL_SENTENCE_TYPE),
      ),
    )
  }

  @Test
  fun `Test recall sentence issued after trancheOneCommencementDate does not trigger validation`() {
    val validationService = getActiveValidationService(
      SentencesExtractionService(),
      TRANCHE_CONFIGURATION,
    )

    val dateAfterTrancheOne = TRANCHE_CONFIGURATION.trancheOneCommencementDate.minusDays(1)
    val lrOraSentence = LR_ORA.copy(sentencedAt = dateAfterTrancheOne)

    // Set the release date to AFTER the trancheOneCommencementDate
    lrOraSentence.sentenceCalculation = SENTENCE_CALCULATION.copy(
      unadjustedHistoricDeterminateReleaseDate = dateAfterTrancheOne,
    )
    var workingBooking = booking.copy(
      sentences = listOf(
        lrOraSentence,
      ),
      adjustments = Adjustments(),
    )
    lrOraSentence.releaseDateTypes =
      ReleaseDateTypes(listOf(ReleaseDateType.TUSED), lrOraSentence, workingBooking.offender)

    workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)

    val result = validationService.validateBookingAfterCalculation(
      workingBooking,
    )

    assertThat(result).isEmpty()
  }

  @Test
  fun `Test recall sentence issued before trancheOneCommencementDate does trigger validation`() {
    val validationService = getActiveValidationService(
      SentencesExtractionService(),
      TRANCHE_CONFIGURATION,
    )

    val dateBeforeTrancheOne = TRANCHE_CONFIGURATION.trancheOneCommencementDate.minusDays(1)
    val dateAfterTrancheOne = TRANCHE_CONFIGURATION.trancheOneCommencementDate.plusDays(1)
    val lrOraSentence = LR_ORA.copy(sentencedAt = dateBeforeTrancheOne)

    // Set the release date to AFTER the trancheOneCommencementDate
    lrOraSentence.sentenceCalculation = SENTENCE_CALCULATION.copy(
      unadjustedHistoricDeterminateReleaseDate = dateAfterTrancheOne,
    )

    var workingBooking = booking.copy(
      sentences = listOf(
        lrOraSentence,
      ),
      adjustments = Adjustments(),
    )
    lrOraSentence.releaseDateTypes =
      ReleaseDateTypes(listOf(ReleaseDateType.TUSED), lrOraSentence, workingBooking.offender)

    workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)

    val result = validationService.validateBookingAfterCalculation(
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
    val validationService = getActiveValidationService(
      SentencesExtractionService(),
      ValidationServiceTest.TRANCHE_CONFIGURATION,
    )
    val lrOraSentence = LR_ORA.copy()

    lrOraSentence.sentenceCalculation = ValidationServiceTest.SENTENCE_CALCULATION.copy(
      unadjustedHistoricDeterminateReleaseDate = LocalDate.of(2024, 9, 11),
    )
    var workingBooking = booking.copy(
      sentences = listOf(
        lrOraSentence,
      ),
      adjustments = Adjustments(),
    )
    lrOraSentence.releaseDateTypes =
      ReleaseDateTypes(listOf(ReleaseDateType.CRD), lrOraSentence, workingBooking.offender)

    workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)

    val result = validationService.validateBookingAfterCalculation(
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
    val FIRST_MAY_2018: LocalDate = LocalDate.of(2018, 5, 1)
    val FIVE_YEAR_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L))
    val FIRST_JAN_2015: LocalDate = LocalDate.of(2015, 1, 1)
    val DOB: LocalDate = LocalDate.of(1980, 1, 1)
    val TRANCHE_CONFIGURATION = SDS40TrancheConfiguration(LocalDate.of(2024, 9, 10), LocalDate.of(2024, 10, 22))

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
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
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
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
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
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
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

    private fun getActiveValidationService(sentencesExtractionService: SentencesExtractionService, trancheConfiguration: SDS40TrancheConfiguration, botus: Boolean = true): ValidationService {
      val featureToggles = FeatureToggles(botus, true, false, sds40ConsecutiveManualJourney = true)
      val validationUtilities = ValidationUtilities()
      val fineValidationService = FineValidationService(validationUtilities)
      val adjustmentValidationService = AdjustmentValidationService(trancheConfiguration)
      val dtoValidationService = DtoValidationService()
      val botusValidationService = BotusValidationService()
      val recallValidationService = RecallValidationService(trancheConfiguration)
      val unsupportedValidationService = UnsupportedValidationService()
      val postCalculationValidationService = PostCalculationValidationService(trancheConfiguration, featureToggles)
      val section91ValidationService = Section91ValidationService(validationUtilities)
      val sopcValidationService = SOPCValidationService(validationUtilities)
      val edsValidationService = EDSValidationService(validationUtilities)
      val manageOffencesService = mock<ManageOffencesService>()
      val toreraValidationService = ToreraValidationService(manageOffencesService)
      val sentenceValidationService = SentenceValidationService(
        validationUtilities,
        sentencesExtractionService,
        section91ValidationService = section91ValidationService,
        sopcValidationService = sopcValidationService,
        fineValidationService,
        edsValidationService = edsValidationService,
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
      )
    }
  }
}
