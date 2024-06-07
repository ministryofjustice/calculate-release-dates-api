package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

@ExtendWith(MockitoExtension::class)
class BookingExtractionServiceTest {

  private val mockSentencesExtractionService: SentencesExtractionService = mock<SentencesExtractionService>()

  private val prisonerId = "A123456A"
  private val sequence = 153
  val lineSequence = 154
  val caseSequence = 155
  val bookingId = 123456L
  private val consecutiveTo = 99
  val offenceCode = "RR1"
  val offences = listOf(
    OffenderOffence(
      offenderChargeId = 1L,
      offenceStartDate = BookingExtractionServiceTest.FIRST_JAN_2015,
      offenceCode = offenceCode,
      offenceDescription = "Littering",
    ),
  )
  private val sentenceAndOffences = SentenceAndOffenceWithReleaseArrangements(
    PrisonApiSentenceAndOffences(
      bookingId = bookingId,
      sentenceSequence = sequence,
      lineSequence = lineSequence,
      caseSequence = caseSequence,
      consecutiveToSequence = consecutiveTo,
      sentenceDate = BookingExtractionServiceTest.FIRST_JAN_2015,
      terms = listOf(
        SentenceTerms(years = 5),
      ),
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.FTRSCH18.name,
      sentenceTypeDescription = "28 day fixed term recall",
      offences = offences,
    ),
    offences[0],
    false,
    SDSEarlyReleaseExclusionType.NO,
  )
  private val bookingAndSentenceAdjustment = BookingAndSentenceAdjustments(
    bookingAdjustments = listOf(
      BookingAdjustment(
        active = true,
        numberOfDays = 5,
        type = BookingAdjustmentType.UNLAWFULLY_AT_LARGE,
        fromDate = BookingExtractionServiceTest.FIRST_JAN_2015.minusDays(6),
        toDate = BookingExtractionServiceTest.FIRST_JAN_2015.minusDays(1),
      ),
    ),
    sentenceAdjustments = listOf(
      SentenceAdjustment(
        active = true,
        sentenceSequence = sequence,
        numberOfDays = 6,
        type = SentenceAdjustmentType.REMAND,
        fromDate = BookingExtractionServiceTest.FIRST_JAN_2015.minusDays(7),
        toDate = BookingExtractionServiceTest.FIRST_JAN_2015.minusDays(1),

        ),
      SentenceAdjustment(
        active = true,
        sentenceSequence = sequence,
        numberOfDays = 22,
        type = SentenceAdjustmentType.UNUSED_REMAND,
      ),
    ),
  )
  private val prisonerDetails = PrisonerDetails(
    bookingId,
    prisonerId,
    dateOfBirth = BookingExtractionServiceTest.DOB,
    firstName = "Harry",
    lastName = "Houdini",
  )
  val returnToCustodyDate = ReturnToCustodyDate(bookingId, LocalDate.of(2022, 3, 15))

  @Test
    fun extract() {
       val hdcedConfiguration = CalculationParamsTestConfigHelper.hdcedConfigurationForTests()
       val hdced4Configuration = CalculationParamsTestConfigHelper.hdced4ConfigurationForTests()
       val bookingExtractionServiceUnderTest: BookingExtractionService = BookingExtractionService(mockSentencesExtractionService, hdcedConfiguration, hdced4Configuration)

      val testBooking = Booking(
          bookingId = 123456,
          returnToCustodyDate = returnToCustodyDate.returnToCustodyDate,
          offender = Offender(
            dateOfBirth = BookingExtractionServiceTest.DOB,
            reference = prisonerId,
          ),
          sentences = mutableListOf(
            StandardDeterminateSentence(
              sentencedAt = BookingExtractionServiceTest.FIRST_JAN_2015,
              duration = BookingExtractionServiceTest.FIVE_YEAR_DURATION,
              offence = Offence(
                committedAt = BookingExtractionServiceTest.FIRST_JAN_2015,
                offenceCode = offenceCode,
              ),
              identifier = UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray()),
              consecutiveSentenceUUIDs = mutableListOf(
                UUID.nameUUIDFromBytes(("$bookingId-$consecutiveTo").toByteArray()),
              ),
              lineSequence = 1,
              caseSequence = 1,
              recallType = RecallType.FIXED_TERM_RECALL_28,
              isSDSPlus = false,
              hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
            ),
          ),
          adjustments = Adjustments(
            mutableMapOf(
              AdjustmentType.UNLAWFULLY_AT_LARGE to mutableListOf(
                Adjustment(
                  appliesToSentencesFrom = BookingExtractionServiceTest.FIRST_JAN_2015.minusDays(6),
                  numberOfDays = 5,
                  fromDate = BookingExtractionServiceTest.FIRST_JAN_2015.minusDays(6),
                  toDate = BookingExtractionServiceTest.FIRST_JAN_2015.minusDays(1),
                ),
              ),
              AdjustmentType.REMAND to mutableListOf(
                Adjustment(
                  appliesToSentencesFrom = BookingExtractionServiceTest.FIRST_JAN_2015,
                  numberOfDays = 6,
                  fromDate = BookingExtractionServiceTest.FIRST_JAN_2015.minusDays(7),
                  toDate = BookingExtractionServiceTest.FIRST_JAN_2015.minusDays(1),
                ),
              ),
            ),
          ))

      testBooking.sentences[0].sentenceCalculation = SentenceCalculation()
      val result = bookingExtractionServiceUnderTest.extract(testBooking)
    }

  private companion object {
    val FIVE_YEAR_DURATION = Duration(mutableMapOf(ChronoUnit.DAYS to 0L, ChronoUnit.WEEKS to 0L, ChronoUnit.MONTHS to 0L, ChronoUnit.YEARS to 5L))
    val FIRST_JAN_2015: LocalDate = LocalDate.of(2015, 1, 1)
    val DOB: LocalDate = LocalDate.of(1980, 1, 1)
  }
}
