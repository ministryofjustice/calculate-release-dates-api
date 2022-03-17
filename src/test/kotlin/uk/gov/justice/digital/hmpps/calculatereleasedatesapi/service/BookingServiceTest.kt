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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessages
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.UUID

class BookingServiceTest {
  private val validationService = mock<ValidationService>()
  private val bookingService = BookingService(validationService)

  @BeforeEach
  fun reset() {
    reset(validationService)
  }

  @Test
  @Suppress("LongMethod")
  fun `A booking object is generated correctly when requesting a booking for a prisonerId`() {
    val prisonerId = "A123456A"
    val sequence = 153
    val lineSequence = 154
    val caseSequence = 155
    val bookingId = 123456L
    val consecutiveTo = 99
    val offences = listOf(
      OffenderOffence(
        offenderChargeId = 1L,
        offenceStartDate = FIRST_JAN_2015,
        offenceCode = "RR1",
        offenceDescription = "Littering"
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
    val bookingAndSentenceAdjustments = BookingAndSentenceAdjustments(
      bookingAdjustments = listOf(
        BookingAdjustments(
          active = true,
          numberOfDays = 5,
          type = BookingAdjustmentType.UNLAWFULLY_AT_LARGE,
          fromDate = FIRST_JAN_2015.minusDays(6),
          toDate = FIRST_JAN_2015.minusDays(1)
        )
      ),
      sentenceAdjustments = listOf(
        SentenceAdjustments(
          active = true,
          sentenceSequence = sequence,
          numberOfDays = 6,
          type = SentenceAdjustmentType.REMAND,
          fromDate = FIRST_JAN_2015.minusDays(7),
          toDate = FIRST_JAN_2015.minusDays(1)

        ),
        SentenceAdjustments(
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
    val sourceData = PrisonApiSourceData(listOf(sentenceAndOffences), prisonerDetails, bookingAndSentenceAdjustments, returnToCustodyDate)
    whenever(validationService.validate(sourceData)).thenReturn(ValidationMessages(ValidationType.VALID))

    val result = bookingService.getBooking(sourceData)

    assertThat(result).isEqualTo(
      Booking(
        bookingId = 123456,
        returnToCustodyDate = returnToCustodyDate.returnToCustodyDate,
        offender = Offender(
          dateOfBirth = DOB,
          reference = prisonerId,
        ),
        sentences = mutableListOf(
          Sentence(
            sentencedAt = FIRST_JAN_2015,
            duration = FIVE_YEAR_DURATION,
            offence = Offence(committedAt = FIRST_JAN_2015),
            identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
            consecutiveSentenceUUIDs = mutableListOf(
              UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray())
            ),
            lineSequence = lineSequence,
            caseSequence = caseSequence,
            sentenceType = SentenceType.FIXED_TERM_RECALL_28
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
