package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.left
import arrow.core.right
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.BreakdownChangedSinceLastCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.SourceDataMissingException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.UnsupportedCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BreakdownMissingReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class CalculationBreakdownServiceTest {

  private val calculationTransactionalService = mock<CalculationTransactionalService>()
  private val sourceDataMapper = mock<SourceDataMapper>()
  private val bookingService = mock<BookingService>()
  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val service = CalculationBreakdownService(sourceDataMapper, calculationTransactionalService, bookingService, calculationRequestRepository)
  private val objectMapper = TestUtil.objectMapper()

  @Test
  fun `should return missing breakdown and sentences and offences if sentences and offences are missing`() {
    val calculationRequestWithEverythingForBreakdown = calculationRequestWithOutcomes().copy(
      prisonerDetails = objectToJson(prisonerDetails, objectMapper),
      sentenceAndOffences = null,
      adjustments = objectToJson(adjustments, objectMapper),
      calculationOutcomes = listOf(
        CalculationOutcome(calculationRequestId = CALCULATION_REQUEST_ID, calculationDateType = "CRD", outcomeDate = LocalDate.of(2026, 6, 26)),
      ),
    )
    whenever(sourceDataMapper.getSourceData(calculationRequestWithEverythingForBreakdown)).thenAnswer { throw SourceDataMissingException("asd") }

    val results = service.getBreakdownSafely(calculationRequestWithEverythingForBreakdown)
    assertThat(results).isEqualTo(BreakdownMissingReason.PRISON_API_DATA_MISSING.left())
    verify(sourceDataMapper, never()).mapSentencesAndOffences(calculationRequestWithEverythingForBreakdown)
  }

  @Test
  fun `should return missing breakdown if algorithm has changed and breakdown can't be generated`() {
    val calculationRequestWithEverythingForBreakdown = calculationRequestWithOutcomes().copy(
      prisonerDetails = objectToJson(prisonerDetails, objectMapper),
      sentenceAndOffences = objectToJson(listOf(originalSentence), objectMapper),
      adjustments = objectToJson(adjustments, objectMapper),
      calculationOutcomes = listOf(
        CalculationOutcome(calculationRequestId = CALCULATION_REQUEST_ID, calculationDateType = "CRD", outcomeDate = LocalDate.of(2026, 6, 26)),
      ),
    )
    val sourceData = mock<CalculationSourceData>()
    val booking = mock<Booking>()
    whenever(sourceDataMapper.getSourceData(calculationRequestWithEverythingForBreakdown)).thenReturn(sourceData)
    whenever(bookingService.getBooking(eq(sourceData))).thenReturn(booking)
    whenever(calculationTransactionalService.calculateWithBreakdown(eq(booking), any(), any())).then {
      throw BreakdownChangedSinceLastCalculation("Calculation no longer agrees with algorithm.")
    }

    val results = service.getBreakdownSafely(calculationRequestWithEverythingForBreakdown)
    assertThat(results).isEqualTo(BreakdownMissingReason.BREAKDOWN_CHANGED_SINCE_LAST_CALCULATION.left())
  }

  @Test
  fun `should return missing breakdown if breakdown generation is unsupported`() {
    val calculationRequestWithEverythingForBreakdown = calculationRequestWithOutcomes().copy(
      prisonerDetails = objectToJson(prisonerDetails, objectMapper),
      sentenceAndOffences = objectToJson(listOf(originalSentence), objectMapper),
      adjustments = objectToJson(adjustments, objectMapper),
      calculationOutcomes = listOf(
        CalculationOutcome(calculationRequestId = CALCULATION_REQUEST_ID, calculationDateType = "CRD", outcomeDate = LocalDate.of(2026, 6, 26)),
      ),
    )
    val sourceData = mock<CalculationSourceData>()
    val booking = mock<Booking>()
    whenever(sourceDataMapper.getSourceData(calculationRequestWithEverythingForBreakdown)).thenReturn(sourceData)
    whenever(bookingService.getBooking(eq(sourceData))).thenReturn(booking)
    whenever(calculationTransactionalService.calculateWithBreakdown(eq(booking), any(), any())).then {
      throw UnsupportedCalculationBreakdown("Bang!")
    }

    val results = service.getBreakdownSafely(calculationRequestWithEverythingForBreakdown)
    assertThat(results).isEqualTo(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
  }

  @Test
  fun `should return breakdown if we have all the data and it hasn't changed`() {
    val calculationRequestWithEverythingForBreakdown = calculationRequestWithOutcomes().copy(
      prisonerDetails = objectToJson(prisonerDetails, objectMapper),
      sentenceAndOffences = objectToJson(listOf(originalSentence), objectMapper),
      adjustments = objectToJson(adjustments, objectMapper),
      calculationOutcomes = listOf(
        CalculationOutcome(calculationRequestId = CALCULATION_REQUEST_ID, calculationDateType = "CRD", outcomeDate = LocalDate.of(2026, 6, 26)),
      ),
    )
    val expectedBreakdown = CalculationBreakdown(emptyList(), null, mapOf(ReleaseDateType.CRD to ReleaseDateCalculationBreakdown(emptySet())), mapOf(ReleaseDateType.PRRD to LocalDate.of(2026, 6, 27)))

    val sourceData = mock<CalculationSourceData>()
    val booking = mock<Booking>()
    whenever(sourceDataMapper.getSourceData(calculationRequestWithEverythingForBreakdown)).thenReturn(sourceData)
    whenever(bookingService.getBooking(eq(sourceData))).thenReturn(booking)
    whenever(calculationTransactionalService.calculateWithBreakdown(eq(booking), any(), any())).thenReturn(expectedBreakdown)
    val results = service.getBreakdownSafely(calculationRequestWithEverythingForBreakdown)
    assertThat(results).isEqualTo(expectedBreakdown.right())
  }

  companion object {
    private const val PRISONER_ID = "A1234AJ"
    private const val BOOKING_ID = 12345L
    private const val CALCULATION_REQUEST_ID = 123456L
  }

  private val originalSentence = SentenceAndOffenceWithReleaseArrangements(
    bookingId = 1L,
    sentenceSequence = 3,
    lineSequence = 2,
    caseSequence = 1,
    sentenceDate = ImportantDates.PCSC_COMMENCEMENT_DATE.minusDays(1),
    terms = listOf(
      SentenceTerms(years = 8),
    ),
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.ADIMP.name,
    sentenceTypeDescription = "ADMIP",
    offence = OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "ADIMP_ORA", "description", listOf("A")),
    caseReference = null,
    fineAmount = null,
    courtId = null,
    courtDescription = null,
    courtTypeCode = null,
    consecutiveToSequence = null,
    isSDSPlus = false,
    isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
    isSDSPlusOffenceInPeriod = false,
    hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
  )

  private val prisonerDetails = PrisonerDetails(
    1,
    "asd",
    dateOfBirth = LocalDate.of(1980, 1, 1),
    firstName = "Zimmy",
    lastName = "Cnys",
  )
  private val adjustments = BookingAndSentenceAdjustments(
    bookingAdjustments = emptyList(),
    sentenceAdjustments = listOf(
      SentenceAdjustment(
        sentenceSequence = 1,
        active = true,
        fromDate = LocalDate.of(2021, 1, 30),
        toDate = LocalDate.of(2021, 1, 31),
        numberOfDays = 1,
        type = SentenceAdjustmentType.REMAND,
      ),
    ),
  )
  private val calculationReference: UUID = UUID.fromString("219db65e-d7b7-4c70-9239-98babff7bcd5")
  private val aDate = LocalDate.of(2021, 2, 3)
  private val calculationOutcomeCrd = CalculationOutcome(
    calculationDateType = ReleaseDateType.CRD.name,
    outcomeDate = aDate,
    calculationRequestId = CALCULATION_REQUEST_ID,
  )
  private val calculationOutcomeSed = CalculationOutcome(
    calculationDateType = ReleaseDateType.SED.name,
    outcomeDate = aDate,
    calculationRequestId = CALCULATION_REQUEST_ID,
  )
  private val calcReason = CalculationReason(-1, false, false, "Reason", false, null, null, null, false, false, false, null)

  private fun calculationRequestWithOutcomes() = CalculationRequest(
    id = CALCULATION_REQUEST_ID,
    calculationReference = calculationReference,
    prisonerId = PRISONER_ID,
    bookingId = BOOKING_ID,
    calculationOutcomes = listOf(calculationOutcomeCrd, calculationOutcomeSed),
    calculationStatus = CalculationStatus.CONFIRMED.name,
    inputData = JacksonUtil.toJsonNode(
      "{" + "\"offender\":{" + "\"reference\":\"ABC123D\"," +
        "\"dateOfBirth\":\"1970-03-03\"" + "}," + "\"sentences\":[" +
        "{" + "\"caseSequence\":1," + "\"lineSequence\":2," +
        "\"offence\":{" + "\"committedAt\":\"2013-09-19\"" + "}," + "\"duration\":{" +
        "\"durationElements\":{" + "\"YEARS\":2" + "}" + "}," + "\"sentencedAt\":\"2013-09-21\"" + "}" + "]" + "}",
    ),
    reasonForCalculation = calcReason,
    otherReasonForCalculation = "foo",
    calculatedAt = LocalDateTime.of(2021, 1, 1, 10, 30),
    calculationType = CalculationType.CALCULATED,
  )
}
