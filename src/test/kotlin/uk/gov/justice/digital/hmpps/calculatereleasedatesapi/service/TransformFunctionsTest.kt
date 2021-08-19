package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceTerm
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.YEARS
import java.util.UUID

class TransformFunctionsTest {
  @Test
  fun `Transform a SentenceTerm into a Sentence correctly where there are no consecutive sentences`() {
    val bookingId = 1110022L
    val sequence = 153
    val request = SentenceTerm(
      bookingId = bookingId,
      sentenceSequence = sequence,
      sentenceStartDate = FIRST_JAN_2015,
      startDate = FIRST_JAN_2015,
    )

    assertThat(transform(request)).isEqualTo(
      Sentence(
        sentencedAt = FIRST_JAN_2015,
        duration = ZERO_DURATION,
        offence = Offence(startedAt = FIRST_JAN_2015),
        identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
        consecutiveSentenceUUIDs = mutableListOf()
      )
    )
  }

  @Test
  fun `Transform a SentenceTerm into a Sentence correctly where there is a consecutive sentence`() {
    val bookingId = 1110022L
    val sequence = 153
    val consecutiveTo = 99
    val request = SentenceTerm(
      bookingId = bookingId,
      sentenceSequence = sequence,
      sentenceStartDate = FIRST_JAN_2015,
      startDate = FIRST_JAN_2015,
      consecutiveTo = consecutiveTo
    )

    assertThat(transform(request)).isEqualTo(
      Sentence(
        sentencedAt = FIRST_JAN_2015,
        duration = ZERO_DURATION,
        offence = Offence(startedAt = FIRST_JAN_2015),
        identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
        consecutiveSentenceUUIDs = mutableListOf(UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray()))
      )
    )
  }

  private companion object {
    val ZERO_DURATION = Duration(mutableMapOf(DAYS to 0L, MONTHS to 0L, YEARS to 0L))
    val FIRST_JAN_2015: LocalDate = LocalDate.of(2015, 1, 1)
  }
}
