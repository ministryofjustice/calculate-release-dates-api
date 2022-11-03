package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSentenceUserInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.UUID

class BookingServiceTest {
  private val validationService = mock<ValidationService>()
  private val bookingService = BookingService(validationService)

  val prisonerId = "A123456A"
  val sequence = 153
  val lineSequence = 154
  val caseSequence = 155
  val bookingId = 123456L
  val consecutiveTo = 99
  val offenceCode = "RR1"
  val offences = listOf(
    OffenderOffence(
      offenderChargeId = 1L,
      offenceStartDate = FIRST_JAN_2015,
      offenceCode = offenceCode,
      offenceDescription = "Littering",
      indicators = listOf(OffenderOffence.SCHEDULE_15_LIFE_INDICATOR)
    ),
  )
  val sentenceAndOffences = SentenceAndOffences(
    bookingId = bookingId,
    sentenceSequence = sequence,
    lineSequence = lineSequence,
    caseSequence = caseSequence,
    consecutiveToSequence = consecutiveTo,
    sentenceDate = FIRST_JAN_2015,
    terms = listOf(
      SentenceTerms(years = 5)
    ),
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.FTRSCH18.name,
    sentenceTypeDescription = "28 day fixed term recall",
    offences = offences,
  )
  val bookingAndSentenceAdjustment = BookingAndSentenceAdjustments(
    bookingAdjustments = listOf(
      BookingAdjustment(
        active = true,
        numberOfDays = 5,
        type = BookingAdjustmentType.UNLAWFULLY_AT_LARGE,
        fromDate = FIRST_JAN_2015.minusDays(6),
        toDate = FIRST_JAN_2015.minusDays(1)
      )
    ),
    sentenceAdjustments = listOf(
      SentenceAdjustment(
        active = true,
        sentenceSequence = sequence,
        numberOfDays = 6,
        type = SentenceAdjustmentType.REMAND,
        fromDate = FIRST_JAN_2015.minusDays(7),
        toDate = FIRST_JAN_2015.minusDays(1)

      ),
      SentenceAdjustment(
        active = true,
        sentenceSequence = sequence,
        numberOfDays = 22,
        type = SentenceAdjustmentType.UNUSED_REMAND
      )
    )
  )
  val prisonerDetails = PrisonerDetails(
    bookingId,
    prisonerId,
    dateOfBirth = DOB,
    firstName = "Harry",
    lastName = "Houdini"
  )
  val returnToCustodyDate = ReturnToCustodyDate(bookingId, LocalDate.of(2022, 3, 15))
  private val offenderFineFinePayment = listOf(OffenderFinePayment(bookingId = 1, paymentDate = LocalDate.of(1, 2, 3), paymentAmount = BigDecimal("10000.88")))
  val sourceData = PrisonApiSourceData(listOf(sentenceAndOffences), prisonerDetails, bookingAndSentenceAdjustment, offenderFineFinePayment, returnToCustodyDate)

  @BeforeEach
  fun reset() {
    reset(validationService)
  }

  @Test
  @Suppress("LongMethod")
  fun `A booking object is generated correctly when requesting a booking for a prisonerId`() {
    whenever(validationService.validate(sourceData)).thenReturn(emptyList())

    val result = bookingService.getBooking(sourceData, null)

    assertThat(result).isEqualTo(
      Booking(
        bookingId = 123456,
        returnToCustodyDate = returnToCustodyDate.returnToCustodyDate,
        offender = Offender(
          dateOfBirth = DOB,
          reference = prisonerId,
        ),
        sentences = mutableListOf(
          StandardDeterminateSentence(
            sentencedAt = FIRST_JAN_2015,
            duration = FIVE_YEAR_DURATION,
            offence = Offence(
              committedAt = FIRST_JAN_2015, isScheduleFifteenMaximumLife = true,
              offenceCode = offenceCode
            ),
            identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
            consecutiveSentenceUUIDs = mutableListOf(
              UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray())
            ),
            lineSequence = lineSequence,
            caseSequence = caseSequence,
            recallType = RecallType.FIXED_TERM_RECALL_28
          )
        ),
        adjustments = Adjustments(
          mutableMapOf(
            UNLAWFULLY_AT_LARGE to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015.minusDays(6),
                numberOfDays = 5, fromDate = FIRST_JAN_2015.minusDays(6),
                toDate = FIRST_JAN_2015.minusDays(1)
              )
            ),
            REMAND to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015, numberOfDays = 6,
                fromDate = FIRST_JAN_2015.minusDays(7),
                toDate = FIRST_JAN_2015.minusDays(1)
              )
            )
          )
        )
      )
    )
  }

  @Test
  @Suppress("LongMethod")
  fun `A booking object is generated correctly when requesting a booking for a prisonerId with user input`() {
    whenever(validationService.validate(sourceData)).thenReturn(emptyList())

    val result = bookingService.getBooking(sourceData, CalculationUserInputs(listOf(CalculationSentenceUserInput(sequence, offenceCode, UserInputType.ORIGINAL, true))))

    assertThat(result).isEqualTo(
      Booking(
        bookingId = 123456,
        returnToCustodyDate = returnToCustodyDate.returnToCustodyDate,
        offender = Offender(
          dateOfBirth = DOB,
          reference = prisonerId,
        ),
        sentences = mutableListOf(
          StandardDeterminateSentence(
            sentencedAt = FIRST_JAN_2015,
            duration = FIVE_YEAR_DURATION,
            offence = Offence(
              committedAt = FIRST_JAN_2015, isScheduleFifteenMaximumLife = true,
              offenceCode = offenceCode
            ),
            identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
            consecutiveSentenceUUIDs = mutableListOf(
              UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray())
            ),
            lineSequence = lineSequence,
            caseSequence = caseSequence,
            recallType = RecallType.FIXED_TERM_RECALL_28
          )
        ),
        adjustments = Adjustments(
          mutableMapOf(
            UNLAWFULLY_AT_LARGE to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015.minusDays(6),
                numberOfDays = 5, fromDate = FIRST_JAN_2015.minusDays(6),
                toDate = FIRST_JAN_2015.minusDays(1)
              )
            ),
            REMAND to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015, numberOfDays = 6,
                fromDate = FIRST_JAN_2015.minusDays(7),
                toDate = FIRST_JAN_2015.minusDays(1)
              )
            )
          )
        )
      )
    )
  }

  @Test
  @Suppress("LongMethod")
  fun `A booking object is generated correctly when requesting a booking for a prisonerId with user input of UPDATED`() {
    whenever(validationService.validate(sourceData)).thenReturn(emptyList())

    val result = bookingService.getBooking(sourceData, CalculationUserInputs(listOf(CalculationSentenceUserInput(sequence, offenceCode, UserInputType.UPDATED, true))))

    assertThat(result).isEqualTo(
      Booking(
        bookingId = 123456,
        returnToCustodyDate = returnToCustodyDate.returnToCustodyDate,
        offender = Offender(
          dateOfBirth = DOB,
          reference = prisonerId,
        ),
        sentences = mutableListOf(
          StandardDeterminateSentence(
            sentencedAt = FIRST_JAN_2015,
            duration = FIVE_YEAR_DURATION,
            offence = Offence(
              committedAt = FIRST_JAN_2015, isPcscSdsPlus = true,
              offenceCode = offenceCode
            ),
            identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
            consecutiveSentenceUUIDs = mutableListOf(
              UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray())
            ),
            lineSequence = lineSequence,
            caseSequence = caseSequence,
            recallType = RecallType.FIXED_TERM_RECALL_28
          )
        ),
        adjustments = Adjustments(
          mutableMapOf(
            UNLAWFULLY_AT_LARGE to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015.minusDays(6),
                numberOfDays = 5, fromDate = FIRST_JAN_2015.minusDays(6),
                toDate = FIRST_JAN_2015.minusDays(1)
              )
            ),
            REMAND to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015, numberOfDays = 6,
                fromDate = FIRST_JAN_2015.minusDays(7),
                toDate = FIRST_JAN_2015.minusDays(1)
              )
            )
          )
        )
      )
    )
  }

  @Test
  @Suppress("LongMethod")
  fun `A booking object is generated correctly when requesting a booking for a prisonerId with user input of SECTION 250`() {
    whenever(validationService.validate(sourceData)).thenReturn(emptyList())

    val result = bookingService.getBooking(sourceData, CalculationUserInputs(listOf(CalculationSentenceUserInput(sequence, offenceCode, UserInputType.SECTION_250, true))))

    assertThat(result).isEqualTo(
      Booking(
        bookingId = 123456,
        returnToCustodyDate = returnToCustodyDate.returnToCustodyDate,
        offender = Offender(
          dateOfBirth = DOB,
          reference = prisonerId,
        ),
        sentences = mutableListOf(
          StandardDeterminateSentence(
            sentencedAt = FIRST_JAN_2015,
            duration = FIVE_YEAR_DURATION,
            offence = Offence(
              committedAt = FIRST_JAN_2015, isPcscSec250 = true,
              offenceCode = offenceCode
            ),
            identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
            consecutiveSentenceUUIDs = mutableListOf(
              UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray())
            ),
            lineSequence = lineSequence,
            caseSequence = caseSequence,
            recallType = RecallType.FIXED_TERM_RECALL_28
          )
        ),
        adjustments = Adjustments(
          mutableMapOf(
            UNLAWFULLY_AT_LARGE to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015.minusDays(6),
                numberOfDays = 5, fromDate = FIRST_JAN_2015.minusDays(6),
                toDate = FIRST_JAN_2015.minusDays(1)
              )
            ),
            REMAND to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015, numberOfDays = 6,
                fromDate = FIRST_JAN_2015.minusDays(7),
                toDate = FIRST_JAN_2015.minusDays(1)
              )
            )
          )
        )
      )
    )
  }

  @Test
  @Suppress("LongMethod")
  fun `A booking object is generated correctly when requesting a booking for a prisonerId with user input of FOUR_TO_UNDER_SEVEN`() {
    whenever(validationService.validate(sourceData)).thenReturn(emptyList())

    val result = bookingService.getBooking(sourceData, CalculationUserInputs(listOf(CalculationSentenceUserInput(sequence, offenceCode, UserInputType.FOUR_TO_UNDER_SEVEN, true))))

    assertThat(result).isEqualTo(
      Booking(
        bookingId = 123456,
        returnToCustodyDate = returnToCustodyDate.returnToCustodyDate,
        offender = Offender(
          dateOfBirth = DOB,
          reference = prisonerId
        ),
        sentences = mutableListOf(
          StandardDeterminateSentence(
            sentencedAt = FIRST_JAN_2015,
            duration = FIVE_YEAR_DURATION,
            offence = Offence(
              committedAt = FIRST_JAN_2015, isPcscSds = true,
              offenceCode = offenceCode
            ),
            identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
            consecutiveSentenceUUIDs = mutableListOf(
              UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray())
            ),
            lineSequence = lineSequence,
            caseSequence = caseSequence,
            recallType = RecallType.FIXED_TERM_RECALL_28
          )
        ),
        adjustments = Adjustments(
          mutableMapOf(
            UNLAWFULLY_AT_LARGE to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015.minusDays(6),
                numberOfDays = 5, fromDate = FIRST_JAN_2015.minusDays(6),
                toDate = FIRST_JAN_2015.minusDays(1)
              )
            ),
            REMAND to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015, numberOfDays = 6,
                fromDate = FIRST_JAN_2015.minusDays(7),
                toDate = FIRST_JAN_2015.minusDays(1)
              )
            )
          )
        )
      )
    )
  }
  private companion object {
    val FIVE_YEAR_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L))
    val FIRST_JAN_2015: LocalDate = LocalDate.of(2015, 1, 1)
    val DOB: LocalDate = LocalDate.of(1980, 1, 1)
  }
}
