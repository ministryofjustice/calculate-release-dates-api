package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentType.TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceTerm
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.YEARS
import java.util.UUID

class BookingServiceTest {
  private val prisonApiClient = mock<PrisonApiClient>()
  private val bookingService = BookingService(prisonApiClient)

  @BeforeEach
  fun reset() {
    reset(prisonApiClient)
  }

  @Test
  fun `A booking object is generated correctly when requestiong a booking for a prisonerId`() {
    val prisonerId = "A123456A"
    val sequence = 153
    val bookingId = 123456L
    val consecutiveTo = 99
    val sentenceTerms = listOf(
      SentenceTerm(
        bookingId = bookingId,
        sentenceSequence = sequence,
        sentenceStartDate = FIRST_JAN_2015,
        startDate = FIRST_JAN_2015,
        consecutiveTo = consecutiveTo
      )
    )
    val sentenceAdjustments = SentenceAdjustments(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    whenever(prisonApiClient.getOffenderDetail(prisonerId))
      .thenReturn(
        PrisonerDetails(
          bookingId,
          prisonerId,
          dateOfBirth = DOB,
          firstName = "Harry",
          lastName = "Houdini"
        )
      )
    whenever(prisonApiClient.getSentenceTerms(123456L)).thenReturn(sentenceTerms)
    whenever(prisonApiClient.getSentenceAdjustments(123456L)).thenReturn(sentenceAdjustments)

    val result = bookingService.getBooking(prisonerId)

    assertThat(result).isEqualTo(
      Booking(
        offender = Offender(
          name = "Harry Houdini",
          dateOfBirth = DOB,
          reference = prisonerId,
        ),
        sentences = mutableListOf(
          Sentence(
            sentencedAt = FIRST_JAN_2015,
            duration = ZERO_DURATION,
            offence = Offence(startedAt = FIRST_JAN_2015),
            identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
            consecutiveSentenceUUIDs = mutableListOf(
              UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray())
            )
          )
        ),
        adjustments = mutableMapOf(REMAND to 0, TAGGED_BAIL to 0, UNLAWFULLY_AT_LARGE to 0)
      )
    )
  }

  private companion object {
    val ZERO_DURATION = Duration(mutableMapOf(DAYS to 0L, MONTHS to 0L, YEARS to 0L))
    val FIRST_JAN_2015: LocalDate = LocalDate.of(2015, 1, 1)
    val DOB: LocalDate = LocalDate.of(1980, 1, 1)
  }
}
