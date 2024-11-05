package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.left
import arrow.core.right
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDatesSubmission
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.TrancheOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BreakdownMissingReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOriginalData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedCalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class DetailedCalculationResultsServiceTest {

  private val calculationBreakdownService = mock<CalculationBreakdownService>()
  private val prisonApiDataMapper = mock<PrisonApiDataMapper>()
  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val calculationResultEnrichmentService = mock<CalculationResultEnrichmentService>()
  private val service = DetailedCalculationResultsService(
    calculationBreakdownService,
    prisonApiDataMapper,
    calculationRequestRepository,
    calculationResultEnrichmentService,
  )
  private val objectMapper = TestUtil.objectMapper()

  @Test
  fun `should throw exception if calculation not found in repo`() {
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(Optional.empty())
    assertThrows<EntityNotFoundException>("No calculation results exist for calculationRequestId $CALCULATION_REQUEST_ID") {
      service.findDetailedCalculationResults(CALCULATION_REQUEST_ID)
    }
  }

  @Test
  fun `should return latest approved dates submission as detailed dates`() {
    val base = calculationRequestWithOutcomes().copy(
      prisonerDetails = objectToJson(prisonerDetails, objectMapper),
      sentenceAndOffences = objectToJson(listOf(originalSentence), objectMapper),
      adjustments = objectToJson(adjustments, objectMapper),
      calculationOutcomes = listOf(
        CalculationOutcome(calculationRequestId = CALCULATION_REQUEST_ID, calculationDateType = "CRD", outcomeDate = LocalDate.of(2026, 6, 26)),
      ),
    )
    val calculationRequestWithApprovedDates = base.copy(
      approvedDatesSubmissions = listOf(
        ApprovedDatesSubmission(
          2,
          base,
          base.prisonerId,
          base.bookingId,
          LocalDateTime.now(),
          "foo",
          listOf(
            ApprovedDates(calculationDateType = ReleaseDateType.HDCAD.name, outcomeDate = LocalDate.of(2024, 1, 2)),
            ApprovedDates(calculationDateType = ReleaseDateType.ROTL.name, outcomeDate = LocalDate.of(2024, 1, 2)),
          ),
        ),
        ApprovedDatesSubmission(
          1,
          base,
          base.prisonerId,
          base.bookingId,
          LocalDateTime.now(),
          "foo",
          listOf(
            ApprovedDates(calculationDateType = ReleaseDateType.HDCAD.name, outcomeDate = LocalDate.of(2024, 1, 1)),
          ),
        ),
      ),
      allocatedSDSTranche = TrancheOutcome(calculationRequest = base, tranche = SDSEarlyReleaseTranche.TRANCHE_1, allocatedTranche = SDSEarlyReleaseTranche.TRANCHE_1),
    )
    val enrichedReleaseDates = mapOf(ReleaseDateType.CRD to DetailedDate(ReleaseDateType.CRD, ReleaseDateType.CRD.description, LocalDate.of(2026, 6, 26), emptyList()))
    val expectedBreakdown = CalculationBreakdown(emptyList(), null, mapOf(ReleaseDateType.CRD to ReleaseDateCalculationBreakdown(emptySet())), mapOf(ReleaseDateType.PRRD to LocalDate.of(2026, 6, 27)))
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(Optional.of(calculationRequestWithApprovedDates))
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequestWithApprovedDates)).thenReturn(listOf(originalSentence))
    whenever(prisonApiDataMapper.mapPrisonerDetails(calculationRequestWithApprovedDates)).thenReturn(prisonerDetails)
    whenever(prisonApiDataMapper.mapBookingAndSentenceAdjustments(calculationRequestWithApprovedDates)).thenReturn(adjustments)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(toReleaseDates(calculationRequestWithApprovedDates), listOf(originalSentence), expectedBreakdown)).thenReturn(enrichedReleaseDates)
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(expectedBreakdown.right())
    val results = service.findDetailedCalculationResults(CALCULATION_REQUEST_ID)
    assertThat(results).isEqualTo(
      DetailedCalculationResults(
        expectedCalcContext,
        enrichedReleaseDates,
        mapOf(
          ReleaseDateType.HDCAD to DetailedDate(ReleaseDateType.HDCAD, ReleaseDateType.HDCAD.description, LocalDate.of(2024, 1, 2), emptyList()),
          ReleaseDateType.ROTL to DetailedDate(ReleaseDateType.ROTL, ReleaseDateType.ROTL.description, LocalDate.of(2024, 1, 2), emptyList()),
        ),
        CalculationOriginalData(
          prisonerDetails,
          listOf(originalSentence),
        ),
        expectedBreakdown,
        null,
        SDSEarlyReleaseTranche.TRANCHE_1,
      ),
    )
    verify(calculationResultEnrichmentService).addDetailToCalculationDates(toReleaseDates(calculationRequestWithApprovedDates), listOf(originalSentence), expectedBreakdown)
  }

  @Test
  fun `if breakdown can't be generated then don't pass through but don't blow up either`() {
    val base = calculationRequestWithOutcomes().copy(
      prisonerDetails = objectToJson(prisonerDetails, objectMapper),
      sentenceAndOffences = objectToJson(listOf(originalSentence), objectMapper),
      adjustments = objectToJson(adjustments, objectMapper),
      calculationOutcomes = listOf(
        CalculationOutcome(calculationRequestId = CALCULATION_REQUEST_ID, calculationDateType = "CRD", outcomeDate = LocalDate.of(2026, 6, 26)),
      ),
    )
    val calculationRequestWithApprovedDates = base.copy(
      approvedDatesSubmissions = listOf(
        ApprovedDatesSubmission(
          2,
          base,
          base.prisonerId,
          base.bookingId,
          LocalDateTime.now(),
          "foo",
          listOf(
            ApprovedDates(calculationDateType = ReleaseDateType.HDCAD.name, outcomeDate = LocalDate.of(2024, 1, 2)),
            ApprovedDates(calculationDateType = ReleaseDateType.ROTL.name, outcomeDate = LocalDate.of(2024, 1, 2)),
          ),
        ),
        ApprovedDatesSubmission(
          1,
          base,
          base.prisonerId,
          base.bookingId,
          LocalDateTime.now(),
          "foo",
          listOf(
            ApprovedDates(calculationDateType = ReleaseDateType.HDCAD.name, outcomeDate = LocalDate.of(2024, 1, 1)),
          ),
        ),
      ),
    )
    val enrichedReleaseDates = mapOf(ReleaseDateType.CRD to DetailedDate(ReleaseDateType.CRD, ReleaseDateType.CRD.description, LocalDate.of(2026, 6, 26), emptyList()))
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(Optional.of(calculationRequestWithApprovedDates))
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequestWithApprovedDates)).thenReturn(listOf(originalSentence))
    whenever(prisonApiDataMapper.mapPrisonerDetails(calculationRequestWithApprovedDates)).thenReturn(prisonerDetails)
    whenever(prisonApiDataMapper.mapBookingAndSentenceAdjustments(calculationRequestWithApprovedDates)).thenReturn(adjustments)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(toReleaseDates(calculationRequestWithApprovedDates), listOf(originalSentence), null)).thenReturn(enrichedReleaseDates)
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    val results = service.findDetailedCalculationResults(CALCULATION_REQUEST_ID)
    assertThat(results).isEqualTo(
      DetailedCalculationResults(
        expectedCalcContext,
        enrichedReleaseDates,
        mapOf(
          ReleaseDateType.HDCAD to DetailedDate(ReleaseDateType.HDCAD, ReleaseDateType.HDCAD.description, LocalDate.of(2024, 1, 2), emptyList()),
          ReleaseDateType.ROTL to DetailedDate(ReleaseDateType.ROTL, ReleaseDateType.ROTL.description, LocalDate.of(2024, 1, 2), emptyList()),
        ),
        CalculationOriginalData(
          prisonerDetails,
          listOf(originalSentence),
        ),
        null,
        BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN,
      ),
    )
    verify(calculationResultEnrichmentService).addDetailToCalculationDates(toReleaseDates(calculationRequestWithApprovedDates), listOf(originalSentence), null)
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
    courtDescription = null,
    consecutiveToSequence = null,
    isSDSPlus = false,
    hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
  )

  private val prisonerDetails = PrisonerDetails(
    1,
    "asd",
    dateOfBirth = LocalDate.of(1980, 1, 1),
    firstName = "Harry",
    lastName = "Houdini",
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
  private val calcReason = CalculationReason(-1, false, false, "Reason", false, null, null, null)
  private fun calculationRequestWithOutcomes() = CalculationRequest(
    id = CALCULATION_REQUEST_ID,
    calculationReference = calculationReference,
    prisonerId = PRISONER_ID,
    bookingId = BOOKING_ID,
    calculationStatus = CalculationStatus.CONFIRMED.name,
    calculatedAt = LocalDateTime.of(2021, 1, 1, 10, 30),
    inputData = JacksonUtil.toJsonNode(
      "{" + "\"offender\":{" + "\"reference\":\"ABC123D\"," +
        "\"dateOfBirth\":\"1970-03-03\"" + "}," + "\"sentences\":[" +
        "{" + "\"caseSequence\":1," + "\"lineSequence\":2," +
        "\"offence\":{" + "\"committedAt\":\"2013-09-19\"" + "}," + "\"duration\":{" +
        "\"durationElements\":{" + "\"YEARS\":2" + "}" + "}," + "\"sentencedAt\":\"2013-09-21\"" + "}" + "]" + "}",
    ),
    calculationOutcomes = listOf(calculationOutcomeCrd, calculationOutcomeSed),
    calculationType = CalculationType.CALCULATED,
    reasonForCalculation = calcReason,
    otherReasonForCalculation = "foo",
    fixedTermRecallDetails = null,
  )

  private val expectedCalcContext = CalculationContext(
    CALCULATION_REQUEST_ID,
    BOOKING_ID,
    PRISONER_ID,
    CalculationStatus.CONFIRMED,
    calculationReference,
    calcReason,
    "foo",
    LocalDate.of(2021, 1, 1),
    CalculationType.CALCULATED,
  )

  private fun toReleaseDates(request: CalculationRequest): List<ReleaseDate> = request.calculationOutcomes
    .filter { it.outcomeDate != null }
    .map { ReleaseDate(it.outcomeDate!!, ReleaseDateType.valueOf(it.calculationDateType)) }
}
