package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderKeyDates
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.UUID

class TransformFunctionsTest {

  @Test
  fun `Transform an offenders Sentence and offences into a Sentence correctly where there are multiple offences`() {
    val bookingId = 1110022L
    val sequence = 153
    val lineSequence = 154
    val caseSequence = 155
    val offences = listOf(
      OffenderOffence(
        offenderChargeId = 1L, offenceStartDate = FIRST_JAN_2015,
        offenceCode = "RR1", offenceDescription = "Littering",
        indicators = listOf("An indicator")
      ),
      OffenderOffence(
        offenderChargeId = 2L, offenceStartDate = SECOND_JAN_2015,
        offenceCode = "RR2", offenceDescription = "Jaywalking",
        indicators = listOf(OffenderOffence.SCHEDULE_15_INDICATOR)
      ),
    )
    val request = SentenceAndOffences(
      bookingId = bookingId,
      sentenceSequence = sequence,
      sentenceDate = FIRST_JAN_2015,
      years = 5,
      months = 4,
      weeks = 3,
      days = 2,
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = "SDS",
      sentenceTypeDescription = "Standard Determinate",
      offences = offences,
      lineSequence = lineSequence,
      caseSequence = caseSequence
    )

    assertThat(transform(request)).isEqualTo(
      listOf(
        Sentence(
          sentencedAt = FIRST_JAN_2015,
          duration = FIVE_YEAR_FOUR_MONTHS_THREE_WEEKS_TWO_DAYS_DURATION,
          offence = Offence(committedAt = FIRST_JAN_2015, isScheduleFifteen = false),
          identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
          consecutiveSentenceUUIDs = mutableListOf(),
          lineSequence = lineSequence,
          caseSequence = caseSequence
        ),
        Sentence(
          sentencedAt = FIRST_JAN_2015,
          duration = FIVE_YEAR_FOUR_MONTHS_THREE_WEEKS_TWO_DAYS_DURATION,
          offence = Offence(committedAt = SECOND_JAN_2015, isScheduleFifteen = true),
          identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
          consecutiveSentenceUUIDs = mutableListOf(),
          lineSequence = lineSequence,
          caseSequence = caseSequence
        ),
      )
    )
  }

  @Test
  fun `Transform an offenders Sentence and offences into a Sentence correctly where there are consecutive sentences`() {
    val bookingId = 1110022L
    val sequence = 153
    val lineSequence = 154
    val caseSequence = 155
    val consecutiveTo = 99
    val offences = listOf(
      OffenderOffence(
        offenderChargeId = 1L, offenceStartDate = FIRST_JAN_2015, offenceEndDate = SECOND_JAN_2015,
        offenceCode = "RR1", offenceDescription = "Littering", indicators = listOf()
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
      lineSequence = lineSequence,
      caseSequence = caseSequence
    )

    assertThat(transform(request)).isEqualTo(
      listOf(
        Sentence(
          sentencedAt = FIRST_JAN_2015,
          duration = FIVE_YEAR_DURATION,
          offence = Offence(committedAt = SECOND_JAN_2015),
          identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
          consecutiveSentenceUUIDs = mutableListOf(UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray())),
          lineSequence = lineSequence,
          caseSequence = caseSequence
        ),
      )
    )
  }

  @Test
  fun `Transform a CalculationRequest to a BookingCalculation`() {
    val releaseDatesByType = mutableMapOf(
      CRD to FIRST_JAN_2015,
      SED to SECOND_JAN_2015,
    )

    assertThat(transform(CALCULATION_REQUEST)).isEqualTo(
      BookingCalculation(
        releaseDatesByType,
        CALCULATION_REQUEST_ID
      )
    )
  }

  @Test
  fun `Transform into an offender dates object with a CRD date, SLED, ESED and ESL`() {
    assertThat(
      transform(
        BOOKING_CALCULATION.copy(
          dates = mutableMapOf(CRD to CRD_DATE, SLED to SLED_DATE, ESED to ESED_DATE),
          effectiveSentenceLength = Period.of(6, 2, 3)
        )
      )
    ).isEqualTo(
      OffenderKeyDates(
        conditionalReleaseDate = CRD_DATE,
        sentenceExpiryDate = SLED_DATE,
        licenceExpiryDate = SLED_DATE,
        effectiveSentenceEndDate = ESED_DATE,
        sentenceLength = "06/02/03"
      )
    )
  }

  private companion object {
    val FIVE_YEAR_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L))
    val FIVE_YEAR_FOUR_MONTHS_THREE_WEEKS_TWO_DAYS_DURATION =
      Duration(mutableMapOf(DAYS to 2L, WEEKS to 3L, MONTHS to 4L, YEARS to 5L))
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

    val CRD_DATE: LocalDate = LocalDate.of(2021, 2, 3)
    val SLED_DATE: LocalDate = LocalDate.of(2021, 3, 4)
    val ESED_DATE: LocalDate = LocalDate.of(2021, 5, 5)

    val BOOKING_CALCULATION = BookingCalculation(
      dates = mutableMapOf(CRD to CRD_DATE),
      calculationRequestId = CALCULATION_REQUEST_ID
    )
  }
}
