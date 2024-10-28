package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDatesSubmission
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatusValue
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoOffenceDatesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.MismatchType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.Alert
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.UUID

class TransformFunctionsTest {

  private val objectMapper: ObjectMapper = TestUtil.objectMapper()

  @Test
  fun `Transform an offenders Sentence and offences into a Sentence correctly where there are consecutive sentences`() {
    val bookingId = 1110022L
    val sequence = 153
    val lineSequence = 154
    val caseSequence = 155
    val consecutiveTo = 99
    val offence = OffenderOffence(
      offenderChargeId = 1L,
      offenceStartDate = FIRST_JAN_2015,
      offenceEndDate = SECOND_JAN_2015,
      offenceCode = "RR1",
      offenceDescription = "Littering",
    )
    val request = SentenceAndOffenceWithReleaseArrangements(
      PrisonApiSentenceAndOffences(
        bookingId = bookingId,
        sentenceSequence = sequence,
        consecutiveToSequence = consecutiveTo,
        sentenceDate = FIRST_JAN_2015,
        terms = listOf(
          SentenceTerms(
            years = 5,
          ),
        ),
        sentenceStatus = "IMP",
        sentenceCategory = "CAT",
        sentenceCalculationType = SentenceCalculationType.ADIMP.name,
        sentenceTypeDescription = "Standard Determinate",
        offences = listOf(offence),
        lineSequence = lineSequence,
        caseSequence = caseSequence,
      ),
      offence,
      isSdsPlus = true,
      hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
    )

    assertThat(transform(request, null)).isEqualTo(
      StandardDeterminateSentence(
        sentencedAt = FIRST_JAN_2015,
        sentenceCalculationType = SentenceCalculationType.ADIMP.name,
        duration = FIVE_YEAR_DURATION,
        offence = Offence(committedAt = SECOND_JAN_2015, offenceCode = "RR1"),
        identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
        consecutiveSentenceUUIDs = mutableListOf(UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray())),
        lineSequence = lineSequence,
        caseSequence = caseSequence,
        isSDSPlus = true,
        hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
      ),
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
        calculationStatus = CalculationStatus.PRELIMINARY,
        calculationReference = CALCULATION_REFERENCE,
        calculationReason = CALCULATION_REASON,
        calculationDate = LocalDate.of(2024, 1, 2),
      ),
    )
  }

  @Test
  fun `Transform a CalculationRequest with approved dates`() {
    val releaseDatesByType = mutableMapOf(
      CRD to FIRST_JAN_2015,
      SED to SECOND_JAN_2015,
    )

    val approvedDatesSubmission = ApprovedDatesSubmission(
      calculationRequest = CALCULATION_REQUEST,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING_ID,
      submittedByUsername = "user1",
      submittedAt = LocalDateTime.now(),
      approvedDates = listOf(
        ApprovedDates(
          calculationDateType = "APD",
          outcomeDate = LocalDate.of(2020, 3, 3),
        ),
      ),
    )

    assertThat(transform(CALCULATION_REQUEST.copy(approvedDatesSubmissions = listOf(approvedDatesSubmission)))).isEqualTo(
      CalculatedReleaseDates(
        releaseDatesByType,
        CALCULATION_REQUEST_ID,
        bookingId = BOOKING_ID,
        prisonerId = PRISONER_ID,
        calculationStatus = CalculationStatus.PRELIMINARY,
        approvedDates = mapOf(ReleaseDateType.APD to LocalDate.of(2020, 3, 3)),
        calculationReference = CALCULATION_REFERENCE,
        calculationReason = CALCULATION_REASON,
        calculationDate = LocalDate.of(2024, 1, 2),
      ),
    )
  }

  @Test
  fun `Transform into an offender dates object with a CRD date, SLED, ESED and ESL`() {
    assertThat(
      transform(
        BOOKING_CALCULATION.copy(
          dates = mutableMapOf(CRD to CRD_DATE, SLED to SLED_DATE, ESED to ESED_DATE),
          effectiveSentenceLength = Period.of(6, 2, 3),
        ),
        null,
      ),
    ).isEqualTo(
      OffenderKeyDates(
        conditionalReleaseDate = CRD_DATE,
        sentenceExpiryDate = SLED_DATE,
        licenceExpiryDate = SLED_DATE,
        effectiveSentenceEndDate = ESED_DATE,
        sentenceLength = "06/02/03",
      ),
    )
  }

  @Test
  fun `Transform prisoner details - check active sex offender is set`() {
    assertThat(
      transform(
        PRISONER_DETAILS.copy(alerts = listOf(SEX_OFFENDER_ALERT, RANDOM_ALERT)),
      ),
    ).isEqualTo(
      Offender(
        reference = PRISONER_ID,
        dateOfBirth = DOB,
        isActiveSexOffender = true,
      ),
    )
  }

  @Test
  fun `Transform prisoner details - check active sex offender is not set with another type of alert`() {
    assertThat(
      transform(
        PRISONER_DETAILS.copy(alerts = listOf(RANDOM_ALERT)),
      ),
    ).isEqualTo(
      Offender(
        reference = PRISONER_ID,
        dateOfBirth = DOB,
        isActiveSexOffender = false,
      ),
    )
  }

  @Test
  fun `Transform prisoner details - check active sex offender is not set with no alerts`() {
    assertThat(
      transform(
        PRISONER_DETAILS.copy(alerts = listOf(RANDOM_ALERT)),
      ),
    ).isEqualTo(
      Offender(
        reference = PRISONER_ID,
        dateOfBirth = DOB,
        isActiveSexOffender = false,
      ),
    )
  }

  @Test
  fun `Transform adjustments`() {
    val fromDate = LocalDate.of(2022, 3, 1)
    val toDate = LocalDate.of(2022, 3, 10)
    val recallSentence = NormalisedSentenceAndOffence(
      bookingId = 1L,
      sentenceSequence = 1,
      sentenceDate = FIRST_JAN_2015,
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.LR.name,
      sentenceTypeDescription = "Recall",
      lineSequence = 1,
      caseSequence = 1,
      offence = OffenderOffence(1, LocalDate.of(2020, 4, 1), null, "A123456", "TEST OFFENCE 2"),
      terms = emptyList(),
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = null,
    )

    val standardSentence = NormalisedSentenceAndOffence(
      bookingId = 1L,
      sentenceSequence = 2,
      sentenceDate = SECOND_JAN_2015,
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceTypeDescription = "Recall",
      lineSequence = 1,
      caseSequence = 2,
      offence = OffenderOffence(1, LocalDate.of(2020, 4, 1), null, "A123456", "TEST OFFENCE 2"),
      terms = emptyList(),
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = null,
    )

    val bookingAndSentenceAdjustment = BookingAndSentenceAdjustments(
      sentenceAdjustments = listOf(
        // All adjustment types for sentence 1
        SentenceAdjustment(sentenceSequence = 1, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.REMAND),
        SentenceAdjustment(sentenceSequence = 1, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.TAGGED_BAIL),
        SentenceAdjustment(sentenceSequence = 1, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.RECALL_SENTENCE_REMAND),
        SentenceAdjustment(sentenceSequence = 1, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.RECALL_SENTENCE_TAGGED_BAIL),
        // All adjustment types for sentence 2
        SentenceAdjustment(sentenceSequence = 2, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.REMAND),
        SentenceAdjustment(sentenceSequence = 2, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.TAGGED_BAIL),
        SentenceAdjustment(sentenceSequence = 2, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.RECALL_SENTENCE_REMAND),
        SentenceAdjustment(sentenceSequence = 2, active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = SentenceAdjustmentType.RECALL_SENTENCE_TAGGED_BAIL),
      ),
      bookingAdjustments = listOf(
        BookingAdjustment(active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = BookingAdjustmentType.UNLAWFULLY_AT_LARGE),
        BookingAdjustment(active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED),
        BookingAdjustment(active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED),
        BookingAdjustment(active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = BookingAdjustmentType.LAWFULLY_AT_LARGE),
        BookingAdjustment(active = true, fromDate = fromDate, toDate = toDate, numberOfDays = 5, type = BookingAdjustmentType.SPECIAL_REMISSION),
      ),
    )

    val adjustments = transform(bookingAndSentenceAdjustment, listOf(recallSentence, standardSentence))

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

  @Test
  fun `Transform ComparisonPerson with correct validation messages`() {
    val comparison: Comparison = Comparison(
      1, UUID.randomUUID(), "ref", objectMapper.createObjectNode(),
      "ABC", ComparisonType.MANUAL, LocalDateTime.now(), "User",
      ComparisonStatus(ComparisonStatusValue.COMPLETED),
    )

    var comparisonMismatchSummary: ComparisonOverview =
      transform(comparison, listOf(getComparisonPerson()), objectMapper)

    assertThat(comparisonMismatchSummary.mismatches[0].validationMessages.size).isEqualTo(0)

    val validationMessages = listOf(
      ValidationMessage(ValidationCode.UNSUPPORTED_DTO_RECALL_SEC104_SEC105),
      ValidationMessage(ValidationCode.A_FINE_SENTENCE_CONSECUTIVE),
    )
    comparisonMismatchSummary = transform(comparison, listOf(getComparisonPerson(validationMessages)), objectMapper)
    assertThat(comparisonMismatchSummary.mismatches[0].validationMessages.size).isEqualTo(2)
    assertTrue(comparisonMismatchSummary.mismatches[0].validationMessages.contains(ValidationMessage(ValidationCode.UNSUPPORTED_DTO_RECALL_SEC104_SEC105)))
    assertTrue(comparisonMismatchSummary.mismatches[0].validationMessages.contains(ValidationMessage(ValidationCode.A_FINE_SENTENCE_CONSECUTIVE)))
  }

  private fun getComparisonPerson(validationMessages: List<ValidationMessage>? = emptyList()): ComparisonPerson {
    val comparisonPerson = ComparisonPerson(
      id = 1,
      comparisonId = 1,
      person = "person",
      lastName = "Smith",
      latestBookingId = 25,
      isMatch = false,
      isValid = true,
      isFatal = false,
      mismatchType = MismatchType.RELEASE_DATES_MISMATCH,
      validationMessages = objectMapper.valueToTree(validationMessages),
      calculatedByUsername = ComparisonServiceTest.USERNAME,
      nomisDates = objectMapper.createObjectNode(),
      overrideDates = objectMapper.createObjectNode(),
      breakdownByReleaseDateType = objectMapper.createObjectNode(),
      calculationRequestId = 1,
      sdsPlusSentencesIdentified = objectMapper.createObjectNode(),
      establishment = "ABC",
    )
    return comparisonPerson
  }

  @Test
  fun `Exception thrown when offence has no dates`() {
    val bookingId = 1110022L
    val sequence = 153
    val lineSequence = 154
    val caseSequence = 155
    val consecutiveTo = 99
    val offence = OffenderOffence(
      offenderChargeId = 1L,
      offenceStartDate = null,
      offenceEndDate = null,
      offenceCode = "RR1",
      offenceDescription = "Littering",
    )
    val request = SentenceAndOffenceWithReleaseArrangements(
      PrisonApiSentenceAndOffences(
        bookingId = bookingId,
        sentenceSequence = sequence,
        consecutiveToSequence = consecutiveTo,
        sentenceDate = FIRST_JAN_2015,
        terms = listOf(
          SentenceTerms(
            years = 5,
          ),
        ),
        sentenceStatus = "IMP",
        sentenceCategory = "CAT",
        sentenceCalculationType = SentenceCalculationType.ADIMP.name,
        sentenceTypeDescription = "Standard Determinate",
        offences = listOf(offence),
        lineSequence = lineSequence,
        caseSequence = caseSequence,
      ),
      offence,
      isSdsPlus = true,
      hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
    )

    assertThrows<NoOffenceDatesProvidedException> { transform(request, null) }
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
    val CALCULATION_REASON = CalculationReason(-1, true, false, "Reason", false, "UPDATE", nomisComment = "NOMIS_COMMENT", null)

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
          calculationDateType = "CRD",
        ),
        CalculationOutcome(
          calculationRequestId = CALCULATION_REQUEST_ID,
          outcomeDate = SECOND_JAN_2015,
          calculationDateType = "SED",
        ),
      ),
      reasonForCalculation = CALCULATION_REASON,
      calculatedAt = LocalDateTime.of(2024, 1, 2, 10, 0, 0),
    )

    val CRD_DATE: LocalDate = LocalDate.of(2021, 2, 3)
    val SLED_DATE: LocalDate = LocalDate.of(2021, 3, 4)
    val ESED_DATE: LocalDate = LocalDate.of(2021, 5, 5)

    val BOOKING_CALCULATION = CalculatedReleaseDates(
      dates = mutableMapOf(CRD to CRD_DATE),
      calculationRequestId = CALCULATION_REQUEST_ID,
      bookingId = 1L,
      prisonerId = PRISONER_ID,
      calculationStatus = CalculationStatus.PRELIMINARY,
      calculationReference = UUID.randomUUID(),
      calculationReason = CALCULATION_REASON,
      calculationDate = LocalDate.of(2024, 1, 1),
    )

    val SEX_OFFENDER_ALERT = Alert(
      dateCreated = LocalDate.of(2010, 1, 1),
      alertType = "S",
      alertCode = "SOR",
    )

    val RANDOM_ALERT = Alert(
      dateCreated = LocalDate.of(2010, 1, 1),
      alertType = "X",
      alertCode = "XXY",
    )

    val PRISONER_DETAILS = PrisonerDetails(
      bookingId = BOOKING_ID,
      offenderNo = PRISONER_ID,
      dateOfBirth = DOB,
      firstName = "Harry",
      lastName = "Houdini",
    )
  }
}
