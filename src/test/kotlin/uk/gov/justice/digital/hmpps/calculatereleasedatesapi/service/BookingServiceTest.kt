package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RECALL_REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentAdditionalInfo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalSentenceId
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Recall
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class BookingServiceTest {

  @InjectMocks
  lateinit var bookingService: BookingService

  private val prisonerId = "A123456A"
  private val sequence = 153
  val lineSequence = 154
  val caseSequence = 155
  val bookingId = 123456L
  private val consecutiveTo = 99
  val offenceCode = "RR1"
  val offence = OffenderOffence(
    offenderChargeId = 1L,
    offenceStartDate = FIRST_JAN_2015,
    offenceCode = offenceCode,
    offenceDescription = "Littering",
  )
  private val sentenceAndOffences = SentenceAndOffenceWithReleaseArrangements(
    bookingId = bookingId,
    sentenceSequence = sequence,
    lineSequence = lineSequence,
    caseSequence = caseSequence,
    consecutiveToSequence = consecutiveTo,
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.FTRSCH18.name,
    sentenceTypeDescription = "28 day fixed term recall",
    sentenceDate = FIRST_JAN_2015,
    terms = listOf(
      SentenceTerms(years = 5),
    ),
    offence = offence,
    caseReference = null,
    courtId = null,
    courtDescription = null,
    courtTypeCode = null,
    fineAmount = null,
    revocationDates = emptyList(),
    sdsReleaseArrangements = SDSReleaseArrangements(
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      sdsEarlyReleaseExclusions = emptyList(),
      isSection250 = false,
    ),
  )

  private val prisonerDetails = PrisonerDetails(
    bookingId,
    prisonerId,
    dateOfBirth = DOB,
    firstName = "Zimmy",
    lastName = "Cnys",
  )
  private val adjustmentDtos = listOf(
    AdjustmentDto(
      person = prisonerDetails.offenderNo,
      days = 5,
      effectiveDays = 5,
      adjustmentType = AdjustmentDto.AdjustmentType.UNLAWFULLY_AT_LARGE,
      fromDate = FIRST_JAN_2015.minusDays(6),
      toDate = FIRST_JAN_2015.minusDays(1),
      bookingId = bookingId,
    ),
    AdjustmentDto(
      person = prisonerDetails.offenderNo,
      sentenceSequence = sequence,
      days = 6,
      effectiveDays = 6,
      adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
      fromDate = FIRST_JAN_2015.minusDays(7),
      toDate = FIRST_JAN_2015.minusDays(1),
      bookingId = bookingId,
    ),
    AdjustmentDto(
      person = prisonerDetails.offenderNo,
      sentenceSequence = sequence,
      days = 22,
      effectiveDays = 22,
      adjustmentType = AdjustmentDto.AdjustmentType.UNUSED_DEDUCTIONS,
      bookingId = bookingId,
    ),
  )
  val returnToCustodyDate = ReturnToCustodyDate(bookingId, LocalDate.of(2022, 3, 15))
  private val offenderFineFinePayment = listOf(OffenderFinePayment(bookingId = 1, paymentDate = LocalDate.of(1, 2, 3), paymentAmount = BigDecimal("10000.88")))
  private val sourceData = CalculationSourceData(listOf(sentenceAndOffences), prisonerDetails, AdjustmentsSourceData(adjustmentsApiData = adjustmentDtos), offenderFineFinePayment, returnToCustodyDate)

  @Test
  @Suppress("LongMethod")
  fun `A booking object is generated correctly when requesting a booking for a prisonerId`() {
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
          StandardDeterminateSentence(
            sentencedAt = FIRST_JAN_2015,
            duration = FIVE_YEAR_DURATION,
            offence = Offence(
              committedAt = FIRST_JAN_2015,
              offenceCode = offenceCode,
            ),
            identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
            consecutiveSentenceUUIDs = mutableListOf(
              UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray()),
            ),
            lineSequence = lineSequence,
            caseSequence = caseSequence,
            externalSentenceId = ExternalSentenceId(sentenceSequence = sequence, bookingId = bookingId),
            recall = Recall(RecallType.FIXED_TERM_RECALL_28, returnToCustodyDate = returnToCustodyDate.returnToCustodyDate),
            releaseArrangements = SDSReleaseArrangements(
              isSDSPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSection250 = false,
              sdsEarlyReleaseExclusions = emptyList(),
            ),
          ),
        ),
        adjustments = Adjustments(
          mutableMapOf(
            UNLAWFULLY_AT_LARGE to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015.minusDays(6),
                numberOfDays = 5,
                fromDate = FIRST_JAN_2015.minusDays(6),
                toDate = FIRST_JAN_2015.minusDays(1),
                additionalInfo = AdjustmentAdditionalInfo.UALAdjustmentAdditionalInfo(null),
              ),
            ),
            RECALL_REMAND to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015,
                numberOfDays = 6,
                fromDate = FIRST_JAN_2015.minusDays(7),
                toDate = FIRST_JAN_2015.minusDays(1),
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  @Suppress("LongMethod")
  fun `A booking object is generated correctly when requesting a booking for a prisonerId with user input`() {
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
          StandardDeterminateSentence(
            sentencedAt = FIRST_JAN_2015,
            duration = FIVE_YEAR_DURATION,
            offence = Offence(
              committedAt = FIRST_JAN_2015,
              offenceCode = offenceCode,
            ),
            identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
            consecutiveSentenceUUIDs = mutableListOf(
              UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray()),
            ),
            lineSequence = lineSequence,
            caseSequence = caseSequence,
            externalSentenceId = ExternalSentenceId(sentenceSequence = sequence, bookingId = bookingId),
            recall = Recall(RecallType.FIXED_TERM_RECALL_28, returnToCustodyDate = returnToCustodyDate.returnToCustodyDate),
            releaseArrangements = SDSReleaseArrangements(
              isSDSPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSection250 = false,
              sdsEarlyReleaseExclusions = emptyList(),
            ),
          ),
        ),
        adjustments = Adjustments(
          mutableMapOf(
            UNLAWFULLY_AT_LARGE to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015.minusDays(6),
                numberOfDays = 5,
                fromDate = FIRST_JAN_2015.minusDays(6),
                toDate = FIRST_JAN_2015.minusDays(1),
                additionalInfo = AdjustmentAdditionalInfo.UALAdjustmentAdditionalInfo(null),
              ),
            ),
            RECALL_REMAND to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015,
                numberOfDays = 6,
                fromDate = FIRST_JAN_2015.minusDays(7),
                toDate = FIRST_JAN_2015.minusDays(1),
              ),
            ),
          ),
        ),
      ),
    )
  }

  // User inputs for offence indicators can still be provided to the calculation engine, however the default is to use
  // those obtained from ManagedOffences and the original NOMIS data.
  @Test
  @Suppress("LongMethod")
  fun `A booking object is generated correctly when requesting a booking for a prisonerId with user input of UPDATED`() {
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
          StandardDeterminateSentence(
            sentencedAt = FIRST_JAN_2015,
            duration = FIVE_YEAR_DURATION,
            offence = Offence(
              committedAt = FIRST_JAN_2015,
              offenceCode = offenceCode,
            ),
            identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
            consecutiveSentenceUUIDs = mutableListOf(
              UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray()),
            ),
            lineSequence = lineSequence,
            caseSequence = caseSequence,
            externalSentenceId = ExternalSentenceId(sentenceSequence = sequence, bookingId = bookingId),
            recall = Recall(RecallType.FIXED_TERM_RECALL_28, returnToCustodyDate = returnToCustodyDate.returnToCustodyDate),
            releaseArrangements = SDSReleaseArrangements(
              isSDSPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSection250 = false,
              sdsEarlyReleaseExclusions = emptyList(),
            ),
          ),
        ),
        adjustments = Adjustments(
          mutableMapOf(
            UNLAWFULLY_AT_LARGE to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015.minusDays(6),
                numberOfDays = 5,
                fromDate = FIRST_JAN_2015.minusDays(6),
                toDate = FIRST_JAN_2015.minusDays(1),
                additionalInfo = AdjustmentAdditionalInfo.UALAdjustmentAdditionalInfo(null),
              ),
            ),
            RECALL_REMAND to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015,
                numberOfDays = 6,
                fromDate = FIRST_JAN_2015.minusDays(7),
                toDate = FIRST_JAN_2015.minusDays(1),
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  @Suppress("LongMethod")
  fun `A booking object is generated correctly when requesting a booking for a prisonerId with user input of SECTION 250`() {
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
          StandardDeterminateSentence(
            sentencedAt = FIRST_JAN_2015,
            duration = FIVE_YEAR_DURATION,
            offence = Offence(
              committedAt = FIRST_JAN_2015,
              offenceCode = offenceCode,
            ),
            identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
            consecutiveSentenceUUIDs = mutableListOf(
              UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray()),
            ),
            lineSequence = lineSequence,
            caseSequence = caseSequence,
            externalSentenceId = ExternalSentenceId(sentenceSequence = sequence, bookingId = bookingId),
            recall = Recall(RecallType.FIXED_TERM_RECALL_28, returnToCustodyDate = returnToCustodyDate.returnToCustodyDate),
            releaseArrangements = SDSReleaseArrangements(
              isSDSPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSection250 = false,
              sdsEarlyReleaseExclusions = emptyList(),
            ),
          ),
        ),
        adjustments = Adjustments(
          mutableMapOf(
            UNLAWFULLY_AT_LARGE to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015.minusDays(6),
                numberOfDays = 5,
                fromDate = FIRST_JAN_2015.minusDays(6),
                toDate = FIRST_JAN_2015.minusDays(1),
                additionalInfo = AdjustmentAdditionalInfo.UALAdjustmentAdditionalInfo(null),
              ),
            ),
            RECALL_REMAND to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015,
                numberOfDays = 6,
                fromDate = FIRST_JAN_2015.minusDays(7),
                toDate = FIRST_JAN_2015.minusDays(1),
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  @Suppress("LongMethod")
  fun `A booking object is generated correctly when requesting a booking for a prisonerId with user input of FOUR_TO_UNDER_SEVEN`() {
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
          StandardDeterminateSentence(
            sentencedAt = FIRST_JAN_2015,
            duration = FIVE_YEAR_DURATION,
            offence = Offence(
              committedAt = FIRST_JAN_2015,
              offenceCode = offenceCode,
            ),
            identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
            consecutiveSentenceUUIDs = mutableListOf(
              UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray()),
            ),
            lineSequence = lineSequence,
            caseSequence = caseSequence,
            externalSentenceId = ExternalSentenceId(sentenceSequence = sequence, bookingId = bookingId),
            recall = Recall(RecallType.FIXED_TERM_RECALL_28, returnToCustodyDate = returnToCustodyDate.returnToCustodyDate),
            releaseArrangements = SDSReleaseArrangements(
              isSDSPlus = false,
              isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
              isSection250 = false,
              sdsEarlyReleaseExclusions = emptyList(),
            ),
          ),
        ),
        adjustments = Adjustments(
          mutableMapOf(
            UNLAWFULLY_AT_LARGE to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015.minusDays(6),
                numberOfDays = 5,
                fromDate = FIRST_JAN_2015.minusDays(6),
                toDate = FIRST_JAN_2015.minusDays(1),
                additionalInfo = AdjustmentAdditionalInfo.UALAdjustmentAdditionalInfo(null),
              ),
            ),
            RECALL_REMAND to mutableListOf(
              Adjustment(
                appliesToSentencesFrom = FIRST_JAN_2015,
                numberOfDays = 6,
                fromDate = FIRST_JAN_2015.minusDays(7),
                toDate = FIRST_JAN_2015.minusDays(1),
              ),
            ),
          ),
        ),
      ),
    )
  }

  private companion object {
    val FIVE_YEAR_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L))
    val FIRST_JAN_2015: LocalDate = LocalDate.of(2015, 1, 1)
    val DOB: LocalDate = LocalDate.of(1980, 1, 1)
  }
}
