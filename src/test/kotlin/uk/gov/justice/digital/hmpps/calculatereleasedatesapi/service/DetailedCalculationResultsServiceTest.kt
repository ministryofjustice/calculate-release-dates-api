package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.UnsupportedCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BreakdownMissingReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedCalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class DetailedCalculationResultsServiceTest {

  private val calculationTransactionalService = mock<CalculationTransactionalService>()
  private val prisonApiDataMapper = mock<PrisonApiDataMapper>()
  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val calculationResultEnrichmentService = mock<CalculationResultEnrichmentService>()
  private val service = DetailedCalculationResultsService(
    calculationTransactionalService,
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
  fun `should return missing breakdown and sentences and offences if sentences and offences are missing`() {
    val calculationRequestWithEverythingForBreakdown = calculationRequestWithOutcomes().copy(
      prisonerDetails = objectToJson(prisonerDetails, objectMapper),
      sentenceAndOffences = null,
      adjustments = objectToJson(adjustments, objectMapper),
      calculationOutcomes = listOf(
        CalculationOutcome(calculationRequestId = CALCULATION_REQUEST_ID, calculationDateType = "CRD", outcomeDate = LocalDate.of(2026, 6, 26)),
      ),
    )
    val enrichedReleaseDates = mapOf(ReleaseDateType.CRD to DetailedDate(ReleaseDateType.CRD, ReleaseDateType.CRD.fullName, LocalDate.of(2026, 6, 26), emptyList()))
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(Optional.of(calculationRequestWithEverythingForBreakdown))
    whenever(prisonApiDataMapper.mapPrisonerDetails(calculationRequestWithEverythingForBreakdown)).thenReturn(prisonerDetails)
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequestWithEverythingForBreakdown)).thenReturn(listOf(originalSentence))
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(calculationRequestWithEverythingForBreakdown, null)).thenReturn(enrichedReleaseDates)

    val results = service.findDetailedCalculationResults(CALCULATION_REQUEST_ID)
    assertThat(results).isEqualTo(
      DetailedCalculationResults(
        expectedCalcContext,
        enrichedReleaseDates,
        prisonerDetails,
        null,
        null,
        BreakdownMissingReason.PRISON_API_DATA_MISSING,
      ),
    )
    verify(prisonApiDataMapper, never()).mapSentencesAndOffences(calculationRequestWithEverythingForBreakdown)
  }

  @Test
  fun `should return missing breakdown if prisoner details are missing`() {
    val calculationRequestWithEverythingForBreakdown = calculationRequestWithOutcomes().copy(
      prisonerDetails = null,
      sentenceAndOffences = objectToJson(listOf(originalSentence), objectMapper),
      adjustments = objectToJson(adjustments, objectMapper),
      calculationOutcomes = listOf(
        CalculationOutcome(calculationRequestId = CALCULATION_REQUEST_ID, calculationDateType = "CRD", outcomeDate = LocalDate.of(2026, 6, 26)),
      ),
    )
    val enrichedReleaseDates = mapOf(ReleaseDateType.CRD to DetailedDate(ReleaseDateType.CRD, ReleaseDateType.CRD.fullName, LocalDate.of(2026, 6, 26), emptyList()))
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(Optional.of(calculationRequestWithEverythingForBreakdown))
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequestWithEverythingForBreakdown)).thenReturn(listOf(originalSentence))
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(calculationRequestWithEverythingForBreakdown, null)).thenReturn(enrichedReleaseDates)

    val results = service.findDetailedCalculationResults(CALCULATION_REQUEST_ID)
    assertThat(results).isEqualTo(
      DetailedCalculationResults(
        expectedCalcContext,
        enrichedReleaseDates,
        null,
        listOf(originalSentence),
        null,
        BreakdownMissingReason.PRISON_API_DATA_MISSING,
      ),
    )
    verify(prisonApiDataMapper, never()).mapPrisonerDetails(calculationRequestWithEverythingForBreakdown)
  }

  @Test
  fun `should return missing breakdown if adjustments are missing`() {
    val calculationRequestWithEverythingForBreakdown = calculationRequestWithOutcomes().copy(
      prisonerDetails = objectToJson(prisonerDetails, objectMapper),
      sentenceAndOffences = objectToJson(listOf(originalSentence), objectMapper),
      adjustments = null,
      calculationOutcomes = listOf(
        CalculationOutcome(calculationRequestId = CALCULATION_REQUEST_ID, calculationDateType = "CRD", outcomeDate = LocalDate.of(2026, 6, 26)),
      ),
    )
    val enrichedReleaseDates = mapOf(ReleaseDateType.CRD to DetailedDate(ReleaseDateType.CRD, ReleaseDateType.CRD.fullName, LocalDate.of(2026, 6, 26), emptyList()))
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(Optional.of(calculationRequestWithEverythingForBreakdown))
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequestWithEverythingForBreakdown)).thenReturn(listOf(originalSentence))
    whenever(prisonApiDataMapper.mapPrisonerDetails(calculationRequestWithEverythingForBreakdown)).thenReturn(prisonerDetails)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(calculationRequestWithEverythingForBreakdown, null)).thenReturn(enrichedReleaseDates)

    val results = service.findDetailedCalculationResults(CALCULATION_REQUEST_ID)
    assertThat(results).isEqualTo(
      DetailedCalculationResults(
        expectedCalcContext,
        enrichedReleaseDates,
        prisonerDetails,
        listOf(originalSentence),
        null,
        BreakdownMissingReason.PRISON_API_DATA_MISSING,
      ),
    )
    verify(prisonApiDataMapper, never()).mapBookingAndSentenceAdjustments(calculationRequestWithEverythingForBreakdown)
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
    val enrichedReleaseDates = mapOf(ReleaseDateType.CRD to DetailedDate(ReleaseDateType.CRD, ReleaseDateType.CRD.fullName, LocalDate.of(2026, 6, 26), emptyList()))
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(Optional.of(calculationRequestWithEverythingForBreakdown))
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequestWithEverythingForBreakdown)).thenReturn(listOf(originalSentence))
    whenever(prisonApiDataMapper.mapPrisonerDetails(calculationRequestWithEverythingForBreakdown)).thenReturn(prisonerDetails)
    whenever(prisonApiDataMapper.mapBookingAndSentenceAdjustments(calculationRequestWithEverythingForBreakdown)).thenReturn(adjustments)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(calculationRequestWithEverythingForBreakdown, null)).thenReturn(enrichedReleaseDates)
    whenever(calculationTransactionalService.calculateWithBreakdown(any(), any())).then {
      throw BreakdownChangedSinceLastCalculation("Calculation no longer agrees with algorithm.")
    }

    val results = service.findDetailedCalculationResults(CALCULATION_REQUEST_ID)
    assertThat(results).isEqualTo(
      DetailedCalculationResults(
        expectedCalcContext,
        enrichedReleaseDates,
        prisonerDetails,
        listOf(originalSentence),
        null,
        BreakdownMissingReason.BREAKDOWN_CHANGED_SINCE_LAST_CALCULATION,
      ),
    )
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
    val enrichedReleaseDates = mapOf(ReleaseDateType.CRD to DetailedDate(ReleaseDateType.CRD, ReleaseDateType.CRD.fullName, LocalDate.of(2026, 6, 26), emptyList()))
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(Optional.of(calculationRequestWithEverythingForBreakdown))
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequestWithEverythingForBreakdown)).thenReturn(listOf(originalSentence))
    whenever(prisonApiDataMapper.mapPrisonerDetails(calculationRequestWithEverythingForBreakdown)).thenReturn(prisonerDetails)
    whenever(prisonApiDataMapper.mapBookingAndSentenceAdjustments(calculationRequestWithEverythingForBreakdown)).thenReturn(adjustments)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(calculationRequestWithEverythingForBreakdown, null)).thenReturn(enrichedReleaseDates)
    whenever(calculationTransactionalService.calculateWithBreakdown(any(), any())).then {
      throw UnsupportedCalculationBreakdown("Bang!")
    }

    val results = service.findDetailedCalculationResults(CALCULATION_REQUEST_ID)
    assertThat(results).isEqualTo(
      DetailedCalculationResults(
        expectedCalcContext,
        enrichedReleaseDates,
        prisonerDetails,
        listOf(originalSentence),
        null,
        BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN,
      ),
    )
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
    val enrichedReleaseDates = mapOf(ReleaseDateType.CRD to DetailedDate(ReleaseDateType.CRD, ReleaseDateType.CRD.fullName, LocalDate.of(2026, 6, 26), emptyList()))
    val expectedBreakdown = CalculationBreakdown(emptyList(), null, mapOf(ReleaseDateType.CRD to ReleaseDateCalculationBreakdown(emptySet())), mapOf(ReleaseDateType.PRRD to LocalDate.of(2026, 6, 27)))
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(Optional.of(calculationRequestWithEverythingForBreakdown))
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequestWithEverythingForBreakdown)).thenReturn(listOf(originalSentence))
    whenever(prisonApiDataMapper.mapPrisonerDetails(calculationRequestWithEverythingForBreakdown)).thenReturn(prisonerDetails)
    whenever(prisonApiDataMapper.mapBookingAndSentenceAdjustments(calculationRequestWithEverythingForBreakdown)).thenReturn(adjustments)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(calculationRequestWithEverythingForBreakdown, expectedBreakdown)).thenReturn(enrichedReleaseDates)
    whenever(calculationTransactionalService.calculateWithBreakdown(any(), any())).thenReturn(expectedBreakdown)
    val results = service.findDetailedCalculationResults(CALCULATION_REQUEST_ID)
    assertThat(results).isEqualTo(
      DetailedCalculationResults(
        expectedCalcContext,
        enrichedReleaseDates,
        prisonerDetails,
        listOf(originalSentence),
        expectedBreakdown,
        null,
      ),
    )
    verify(calculationResultEnrichmentService).addDetailToCalculationDates(calculationRequestWithEverythingForBreakdown, expectedBreakdown)
  }

  companion object {
    private const val PRISONER_ID = "A1234AJ"
    private const val BOOKING_ID = 12345L
    private const val CALCULATION_REQUEST_ID = 123456L
  }

  private val originalSentence = SentenceAndOffences(
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
    offences = listOf(OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "ADIMP_ORA", "description", listOf("A"))),
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
}
