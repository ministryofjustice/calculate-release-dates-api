package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.Alert
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
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
        indicators = listOf(OffenderOffence.SCHEDULE_15_LIFE_INDICATOR)
      ),
    )
    val request = SentenceAndOffences(
      bookingId = bookingId,
      sentenceSequence = sequence,
      sentenceDate = FIRST_JAN_2015,
      terms = listOf(
        SentenceTerms(
          years = 5,
          months = 4,
          weeks = 3,
          days = 2
        )
      ),
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceTypeDescription = "Standard Determinate",
      offences = offences,
      lineSequence = lineSequence,
      caseSequence = caseSequence
    )

    assertThat(transform(request)).isEqualTo(
      listOf(
        StandardDeterminateSentence(
          sentencedAt = FIRST_JAN_2015,
          duration = FIVE_YEAR_FOUR_MONTHS_THREE_WEEKS_TWO_DAYS_DURATION,
          offence = Offence(committedAt = FIRST_JAN_2015, isScheduleFifteenMaximumLife = false),
          identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
          consecutiveSentenceUUIDs = mutableListOf(),
          lineSequence = lineSequence,
          caseSequence = caseSequence

        ),
        StandardDeterminateSentence(
          sentencedAt = FIRST_JAN_2015,
          duration = FIVE_YEAR_FOUR_MONTHS_THREE_WEEKS_TWO_DAYS_DURATION,
          offence = Offence(committedAt = SECOND_JAN_2015, isScheduleFifteenMaximumLife = true),
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
      terms = listOf(
        SentenceTerms(
          years = 5
        )
      ),
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceTypeDescription = "Standard Determinate",
      offences = offences,
      lineSequence = lineSequence,
      caseSequence = caseSequence
    )

    assertThat(transform(request)).isEqualTo(
      listOf(
        StandardDeterminateSentence(
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
      CalculatedReleaseDates(
        releaseDatesByType,
        CALCULATION_REQUEST_ID,
        bookingId = BOOKING_ID,
        prisonerId = PRISONER_ID,
        calculationStatus = CalculationStatus.PRELIMINARY
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

  @Test
  fun `Transform prisoner details - check active sex offender is set`() {
    assertThat(
      transform(
        PRISONER_DETAILS.copy(alerts = listOf(SEX_OFFENDER_ALERT, RANDOM_ALERT))
      )
    ).isEqualTo(
      Offender(
        reference = PRISONER_ID,
        dateOfBirth = DOB,
        isActiveSexOffender = true,
      )
    )
  }

  @Test
  fun `Transform prisoner details - check active sex offender is not set with another type of alert`() {
    assertThat(
      transform(
        PRISONER_DETAILS.copy(alerts = listOf(RANDOM_ALERT))
      )
    ).isEqualTo(
      Offender(
        reference = PRISONER_ID,
        dateOfBirth = DOB,
        isActiveSexOffender = false,
      )
    )
  }

  @Test
  fun `Transform prisoner details - check active sex offender is not set with no alerts`() {
    assertThat(
      transform(
        PRISONER_DETAILS.copy(alerts = listOf(RANDOM_ALERT))
      )
    ).isEqualTo(
      Offender(
        reference = PRISONER_ID,
        dateOfBirth = DOB,
        isActiveSexOffender = false,
      )
    )
  }

  @Test
  fun `Transform adjustments`() {
    val fromDate = LocalDate.of(2022, 3, 1)
    val toDate = LocalDate.of(2022, 3, 10)
    val recallSentence = SentenceAndOffences(
      bookingId = 1L,
      sentenceSequence = 1,
      sentenceDate = FIRST_JAN_2015,
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.LR.name,
      sentenceTypeDescription = "Recall",
      lineSequence = 1,
      caseSequence = 1
    )

    val standardSentence = SentenceAndOffences(
      bookingId = 1L,
      sentenceSequence = 2,
      sentenceDate = SECOND_JAN_2015,
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceTypeDescription = "Recall",
      lineSequence = 1,
      caseSequence = 2
    )

    val bookingAndSentenceAdjustments = BookingAndSentenceAdjustments(
      sentenceAdjustments = listOf(
        // All adjustment types for sentence 1
        SentenceAdjustments(sentenceSequence = 1, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.REMAND),
        SentenceAdjustments(sentenceSequence = 1, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.TAGGED_BAIL),
        SentenceAdjustments(sentenceSequence = 1, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.RECALL_SENTENCE_REMAND),
        SentenceAdjustments(sentenceSequence = 1, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.RECALL_SENTENCE_TAGGED_BAIL),
        // All adjustment types for sentence 2
        SentenceAdjustments(sentenceSequence = 2, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.REMAND),
        SentenceAdjustments(sentenceSequence = 2, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.TAGGED_BAIL),
        SentenceAdjustments(sentenceSequence = 2, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.RECALL_SENTENCE_REMAND),
        SentenceAdjustments(sentenceSequence = 2, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.RECALL_SENTENCE_TAGGED_BAIL),
      ),
      bookingAdjustments = listOf(
        BookingAdjustments(active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = BookingAdjustmentType.UNLAWFULLY_AT_LARGE),
        BookingAdjustments(active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED),
        BookingAdjustments(active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED),
        BookingAdjustments(active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = BookingAdjustmentType.LAWFULLY_AT_LARGE),
        BookingAdjustments(active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = BookingAdjustmentType.SPECIAL_REMISSION),
      )
    )

    val adjustments = transform(bookingAndSentenceAdjustments, listOf(recallSentence, standardSentence))

    val remand = adjustments.getOrEmptyList(AdjustmentType.REMAND)
    assertThat(remand.size).isEqualTo(2)
    assertThat(remand[0].appliesToSentencesFrom).isEqualTo(standardSentence.sentenceDate)
    assertThat(remand[0].fromDate).isEqualTo(fromDate)
    assertThat(remand[0].toDate).isEqualTo(toDate)
    assertThat(remand[1].appliesToSentencesFrom).isEqualTo(standardSentence.sentenceDate)
    assertThat(remand[1].fromDate).isEqualTo(fromDate)
    assertThat(remand[1].toDate).isEqualTo(toDate)

    val taggedBail = adjustments.getOrEmptyList(AdjustmentType.TAGGED_BAIL)
    assertThat(taggedBail.size).isEqualTo(2)
    assertThat(taggedBail[0].appliesToSentencesFrom).isEqualTo(standardSentence.sentenceDate)
    assertThat(taggedBail[0].fromDate).isEqualTo(fromDate)
    assertThat(taggedBail[0].toDate).isEqualTo(toDate)
    assertThat(taggedBail[1].appliesToSentencesFrom).isEqualTo(standardSentence.sentenceDate)
    assertThat(taggedBail[1].fromDate).isEqualTo(fromDate)
    assertThat(taggedBail[1].toDate).isEqualTo(toDate)

    val recallRemand = adjustments.getOrEmptyList(AdjustmentType.RECALL_REMAND)
    assertThat(recallRemand.size).isEqualTo(2)
    assertThat(recallRemand[0].appliesToSentencesFrom).isEqualTo(recallSentence.sentenceDate)
    assertThat(recallRemand[0].fromDate).isEqualTo(fromDate)
    assertThat(recallRemand[0].toDate).isEqualTo(toDate)
    assertThat(recallRemand[1].appliesToSentencesFrom).isEqualTo(recallSentence.sentenceDate)
    assertThat(recallRemand[1].fromDate).isEqualTo(fromDate)
    assertThat(recallRemand[1].toDate).isEqualTo(toDate)

    val recallTaggedBail = adjustments.getOrEmptyList(AdjustmentType.RECALL_TAGGED_BAIL)
    assertThat(recallTaggedBail.size).isEqualTo(2)
    assertThat(recallTaggedBail[0].appliesToSentencesFrom).isEqualTo(recallSentence.sentenceDate)
    assertThat(recallTaggedBail[0].fromDate).isEqualTo(fromDate)
    assertThat(recallTaggedBail[0].toDate).isEqualTo(toDate)
    assertThat(recallTaggedBail[1].appliesToSentencesFrom).isEqualTo(recallSentence.sentenceDate)
    assertThat(recallTaggedBail[1].fromDate).isEqualTo(fromDate)
    assertThat(recallTaggedBail[1].toDate).isEqualTo(toDate)

    val unlawfullyAtLarge = adjustments.getOrEmptyList(AdjustmentType.UNLAWFULLY_AT_LARGE)
    assertThat(unlawfullyAtLarge.size).isEqualTo(1)
    assertThat(unlawfullyAtLarge[0].appliesToSentencesFrom).isEqualTo(fromDate)
    assertThat(unlawfullyAtLarge[0].fromDate).isEqualTo(fromDate)
    assertThat(unlawfullyAtLarge[0].toDate).isEqualTo(toDate)

    val restorationOfAdditionalDays = adjustments.getOrEmptyList(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED)
    assertThat(restorationOfAdditionalDays.size).isEqualTo(1)
    assertThat(restorationOfAdditionalDays[0].appliesToSentencesFrom).isEqualTo(fromDate)
    assertThat(restorationOfAdditionalDays[0].fromDate).isEqualTo(fromDate)
    assertThat(restorationOfAdditionalDays[0].toDate).isEqualTo(toDate)

    val additionalDays = adjustments.getOrEmptyList(AdjustmentType.ADDITIONAL_DAYS_AWARDED)
    assertThat(additionalDays.size).isEqualTo(1)
    assertThat(additionalDays[0].appliesToSentencesFrom).isEqualTo(fromDate)
    assertThat(additionalDays[0].fromDate).isEqualTo(fromDate)
    assertThat(additionalDays[0].toDate).isEqualTo(toDate)
  }

  private companion object {
    val FIVE_YEAR_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L))
    val FIVE_YEAR_FOUR_MONTHS_THREE_WEEKS_TWO_DAYS_DURATION =
      Duration(mutableMapOf(DAYS to 2L, WEEKS to 3L, MONTHS to 4L, YEARS to 5L))
    val FIRST_JAN_2015: LocalDate = LocalDate.of(2015, 1, 1)
    val SECOND_JAN_2015: LocalDate = LocalDate.of(2015, 1, 2)
    val DOB: LocalDate = LocalDate.of(1955, 11, 5)
    private const val PRISONER_ID = "A1234AJ"
    private const val BOOKING_ID = 12345L
    private val CALCULATION_REFERENCE: UUID = UUID.randomUUID()
    private const val CALCULATION_REQUEST_ID = 100011L

    val CALCULATION_REQUEST = CalculationRequest(
      id = CALCULATION_REQUEST_ID,
      calculationReference = CALCULATION_REFERENCE,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING_ID,
      calculationStatus = CalculationStatus.PRELIMINARY.name,
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

    val BOOKING_CALCULATION = CalculatedReleaseDates(
      dates = mutableMapOf(CRD to CRD_DATE),
      calculationRequestId = CALCULATION_REQUEST_ID,
      bookingId = 1L,
      prisonerId = PRISONER_ID,
      calculationStatus = CalculationStatus.PRELIMINARY
    )

    val SEX_OFFENDER_ALERT = Alert(
      dateCreated = LocalDate.of(2010, 1, 1),
      alertType = "S",
      alertCode = "SOR"
    )

    val RANDOM_ALERT = Alert(
      dateCreated = LocalDate.of(2010, 1, 1),
      alertType = "X",
      alertCode = "XXY"
    )

    val PRISONER_DETAILS = PrisonerDetails(
      bookingId = BOOKING_ID,
      offenderNo = PRISONER_ID,
      dateOfBirth = DOB,
      firstName = "Harry",
      lastName = "Houdini"
    )
  }
}
