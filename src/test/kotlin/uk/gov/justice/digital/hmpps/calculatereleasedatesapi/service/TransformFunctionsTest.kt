package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffences
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.YEARS
import java.util.UUID

class TransformFunctionsTest {

  @Test
  fun `Transform an offenders Sentence and offences into a Sentence correctly where there are multiple offences`() {
    val bookingId = 1110022L
    val sequence = 153
    val offences = listOf(
      OffenderOffence(
        offenderChargeId = 1L, offenceStartDate = FIRST_JAN_2015, offenceCode = "RR1", offenceDescription = "Littering"
      ),
      OffenderOffence(
        offenderChargeId = 2L, offenceStartDate = SECOND_JAN_2015,
        offenceCode = "RR2", offenceDescription = "Jaywalking"
      ),
    )
    val request = SentenceAndOffences(
      bookingId = bookingId,
      sentenceSequence = sequence,
      sentenceDate = FIRST_JAN_2015,
      years = 5,
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = "SDS",
      sentenceTypeDescription = "Standard Determinate",
      offences = offences,
    )

    assertThat(transform(request)).isEqualTo(
      listOf(
        Sentence(
          sentencedAt = FIRST_JAN_2015,
          duration = FIVE_YEAR_DURATION,
          offence = Offence(committedAt = FIRST_JAN_2015),
          identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
          consecutiveSentenceUUIDs = mutableListOf(),
          sequence = sequence
        ),
        Sentence(
          sentencedAt = FIRST_JAN_2015,
          duration = FIVE_YEAR_DURATION,
          offence = Offence(committedAt = SECOND_JAN_2015),
          identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
          consecutiveSentenceUUIDs = mutableListOf(),
          sequence = sequence
        ),
      )
    )
  }

  @Test
  fun `Transform an offenders Sentence and offences into a Sentence correctly where there are consecutive sentences`() {
    val bookingId = 1110022L
    val sequence = 153
    val consecutiveTo = 99
    val offences = listOf(
      OffenderOffence(
        offenderChargeId = 1L, offenceStartDate = FIRST_JAN_2015, offenceEndDate = SECOND_JAN_2015,
        offenceCode = "RR1", offenceDescription = "Littering"
      ),
    )
    val request = SentenceAndOffences(
      bookingId = bookingId,
      sentenceSequence = sequence,
      consecutiveToSequence = consecutiveTo,
      sentenceDate = FIRST_JAN_2015,
      years = 5,
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = "SDS",
      sentenceTypeDescription = "Standard Determinate",
      offences = offences,
    )

    assertThat(transform(request)).isEqualTo(
      listOf(
        Sentence(
          sentencedAt = FIRST_JAN_2015,
          duration = FIVE_YEAR_DURATION,
          offence = Offence(committedAt = SECOND_JAN_2015),
          identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
          consecutiveSentenceUUIDs = mutableListOf(UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray()))
        ),
      )
    )
  }

  @Test
  fun `Transform a CalculationRequest to a BookingCalculation`() {
    val releaseDatesBySentenceType = mutableMapOf(
      SentenceType.CRD to FIRST_JAN_2015,
      SentenceType.SED to SECOND_JAN_2015,
    )

    assertThat(transform(CALCULATION_REQUEST)).isEqualTo(
      BookingCalculation(
        releaseDatesBySentenceType,
        CALCULATION_REQUEST_ID
      )
    )
  }

  private companion object {
    val FIVE_YEAR_DURATION = Duration(mutableMapOf(DAYS to 0L, MONTHS to 0L, YEARS to 5L))
    val FIRST_JAN_2015: LocalDate = LocalDate.of(2015, 1, 1)
    val SECOND_JAN_2015: LocalDate = LocalDate.of(2015, 1, 2)
    private const val PRISONER_ID = "A1234AJ"
    private const val BOOKING_ID = 12345L
    private val CALCULATION_REFERENCE: UUID = UUID.randomUUID()
    private const val CALCULATION_REQUEST_ID = 100011L

    val CALCULATION_REQUEST = CalculationRequest(
      id = CALCULATION_REQUEST_ID,
      calculationReference = CALCULATION_REFERENCE,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING_ID,
      calculationOutcomes = listOf(
        CalculationOutcome(
          calculationRequestId = CALCULATION_REQUEST_ID,
          outcomeDate = FIRST_JAN_2015,
          calculationDateType = "CRD"
        ),
        CalculationOutcome(
          calculationRequestId = CALCULATION_REQUEST_ID,
          outcomeDate = SECOND_JAN_2015,
          calculationDateType = "SED"
        ),
      )
    )
  }
}
