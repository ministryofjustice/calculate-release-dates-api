package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedCalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NonFridayReleaseDay
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateHint
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.WorkingDay
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

class CalculationResultEnrichmentServiceTest {

  private val nonFridayReleaseService = mock<NonFridayReleaseService>()
  private val workingDayService = mock<WorkingDayService>()

  private val CALCULATION_REQUEST_ID = 123456L
  private val CALCULATION_REFERENCE: UUID = UUID.fromString("219db65e-d7b7-4c70-9239-98babff7bcd5")
  private val PRISONER_ID = "A1234AJ"
  private val BOOKING_ID = 12345L
  private val CALCULATION_REASON = CalculationReason(-1, true, false, "Reason", false, nomisReason = "UPDATE", nomisComment = "NOMIS_COMMENT", null)

  @Test
  fun `should add the full release date type name for all release dates`() {
    val sedDate = LocalDate.of(2021, 2, 3)
    val crdDate = LocalDate.of(2022, 3, 4)

    whenever(nonFridayReleaseService.getDate(ReleaseDate(sedDate, ReleaseDateType.SED))).thenReturn(NonFridayReleaseDay(sedDate, false))
    whenever(nonFridayReleaseService.getDate(ReleaseDate(crdDate, ReleaseDateType.CRD))).thenReturn(NonFridayReleaseDay(crdDate, false))
    whenever(workingDayService.previousWorkingDay(sedDate)).thenReturn(WorkingDay(sedDate, adjustedForWeekend = false, adjustedForBankHoliday = false))
    whenever(workingDayService.previousWorkingDay(crdDate)).thenReturn(WorkingDay(crdDate, adjustedForWeekend = false, adjustedForBankHoliday = false))

    val sedOutcome = calculationOutcome(ReleaseDateType.SED, sedDate)
    val crdOutcome = calculationOutcome(ReleaseDateType.CRD, crdDate)
    val calculationRequest = calculationRequest(listOf(sedOutcome, crdOutcome))
    val results = calculationResultEnrichmentService().addDetailToCalculationResults(calculationRequest, null)
    assertThat(results).isEqualTo(
      DetailedCalculationResults(
        calculationRequest.id,
        mapOf(
          ReleaseDateType.SED to DetailedReleaseDate(ReleaseDateType.SED, "Sentence expiry date", sedDate, emptyList()),
          ReleaseDateType.CRD to DetailedReleaseDate(ReleaseDateType.CRD, "Conditional release date", crdDate, emptyList()),
        ),
      ),
    )
  }

  @ParameterizedTest
  @EnumSource(ReleaseDateType::class)
  fun `every release date type has a description`(type: ReleaseDateType) {
    val date = LocalDate.of(2021, 2, 3)
    whenever(nonFridayReleaseService.getDate(ReleaseDate(date, type))).thenReturn(NonFridayReleaseDay(date, false))
    whenever(workingDayService.previousWorkingDay(date)).thenReturn(WorkingDay(date, adjustedForWeekend = false, adjustedForBankHoliday = false))
    val outcome = calculationOutcome(type, date)
    val calculationRequest = calculationRequest(listOf(outcome))
    val results = calculationResultEnrichmentService().addDetailToCalculationResults(calculationRequest, null)
    assertThat(results.dates[type]?.releaseDateTypeFullName).isNotBlank()
  }

  @Test
  fun `should calculate non friday release date adjustments for relevant dates`() {
    val type = ReleaseDateType.CRD
    val originalDate = LocalDate.of(2021, 2, 3)
    val adjustedDate = LocalDate.of(2021, 2, 1)

    whenever(nonFridayReleaseService.getDate(ReleaseDate(originalDate, type))).thenReturn(NonFridayReleaseDay(adjustedDate, true))

    val outcome = calculationOutcome(type, originalDate)
    val calculationRequest = calculationRequest(listOf(outcome))
    val results = calculationResultEnrichmentService().addDetailToCalculationResults(calculationRequest, null)
    assertThat(results.dates[type]?.hints).isEqualTo(
      listOf(
        ReleaseDateHint(
          "The Discretionary Friday/Pre-Bank Holiday Release Scheme Policy applies to this release date.",
          "https://www.gov.uk/government/publications/discretionary-fridaypre-bank-holiday-release-scheme-policy-framework",
        ),
      ),
    )
    verify(workingDayService, never()).previousWorkingDay(any()) /* Only checks weekend if non-working day doesn't apply */
  }

  @ParameterizedTest
  @CsvSource(
    "CRD,true",
    "ARD,true",
    "PRRD,true",
    "HDCED,true",
    "PED,true",
    "ETD,true",
    "MTD,true",
    "LTD,true",
    "LED,false",
  )
  fun `should calculate weekend adjustments for relevant dates`(type: ReleaseDateType, expected: Boolean) {
    val originalDate = LocalDate.of(2021, 2, 3)
    val adjustedDate = LocalDate.of(2021, 2, 1)

    whenever(nonFridayReleaseService.getDate(ReleaseDate(originalDate, type))).thenReturn(NonFridayReleaseDay(originalDate, false))
    whenever(workingDayService.previousWorkingDay(originalDate)).thenReturn(WorkingDay(adjustedDate, adjustedForWeekend = false, adjustedForBankHoliday = false))

    val outcome = calculationOutcome(type, originalDate)
    val calculationRequest = calculationRequest(listOf(outcome))
    val results = calculationResultEnrichmentService().addDetailToCalculationResults(calculationRequest, null)
    assertThat(results.dates[type]?.hints).isEqualTo(
      if (expected) {
        listOf(ReleaseDateHint("Monday, 01 February 2021 when adjusted to a working day"))
      } else {
        emptyList()
      },
    )
  }

  @Test
  fun `should not calculate weekend adjustments for past dates`() {
    val type = ReleaseDateType.CRD
    val originalDate = LocalDate.of(2021, 2, 3)
    val adjustedDate = LocalDate.of(2021, 2, 1)
    val today = LocalDate.of(2021, 2, 4)

    whenever(nonFridayReleaseService.getDate(ReleaseDate(originalDate, type))).thenReturn(NonFridayReleaseDay(originalDate, false))
    whenever(workingDayService.previousWorkingDay(originalDate)).thenReturn(WorkingDay(adjustedDate, adjustedForWeekend = false, adjustedForBankHoliday = false))

    val outcome = calculationOutcome(type, originalDate)
    val calculationRequest = calculationRequest(listOf(outcome))
    val results = calculationResultEnrichmentService(today).addDetailToCalculationResults(calculationRequest, null)
    assertThat(results.dates[type]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  private fun calculationOutcome(type: ReleaseDateType, date: LocalDate) = CalculationOutcome(
    calculationDateType = type.name,
    outcomeDate = date,
    calculationRequestId = CALCULATION_REQUEST_ID,
  )

  private fun calculationRequest(calculationOutcomes: List<CalculationOutcome>) = CalculationRequest(
    id = CALCULATION_REQUEST_ID,
    calculationReference = CALCULATION_REFERENCE,
    prisonerId = PRISONER_ID,
    bookingId = BOOKING_ID,
    calculationOutcomes = calculationOutcomes,
    calculationStatus = CalculationStatus.CONFIRMED.name,
    inputData = JacksonUtil.toJsonNode(
      "{" + "\"offender\":{" + "\"reference\":\"ABC123D\"," +
        "\"dateOfBirth\":\"1970-03-03\"" + "}," + "\"sentences\":[" +
        "{" + "\"caseSequence\":1," + "\"lineSequence\":2," +
        "\"offence\":{" + "\"committedAt\":\"2013-09-19\"" + "}," + "\"duration\":{" +
        "\"durationElements\":{" + "\"YEARS\":2" + "}" + "}," + "\"sentencedAt\":\"2013-09-21\"" + "}" + "]" + "}",
    ),
    reasonForCalculation = CALCULATION_REASON,
  )

  private fun calculationResultEnrichmentService(today: LocalDate = LocalDate.of(2000, 1, 1)): CalculationResultEnrichmentService {
    val clock = Clock.fixed(today.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
    return CalculationResultEnrichmentService(nonFridayReleaseService, workingDayService, clock)
  }
}
