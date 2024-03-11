package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
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
import java.time.LocalDate
import java.util.*

class CalculationResultEnrichmentServiceTest {

  private val nonFridayReleaseService = mock<NonFridayReleaseService>()
  private val service = CalculationResultEnrichmentService(nonFridayReleaseService)

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

    val sedOutcome = calculationOutcome(ReleaseDateType.SED, sedDate)
    val crdOutcome = calculationOutcome(ReleaseDateType.CRD, crdDate)
    val calculationRequest = calculationRequest(listOf(sedOutcome, crdOutcome))
    val results = service.addDetailToCalculationResults(calculationRequest)
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
    val outcome = calculationOutcome(type, date)
    val calculationRequest = calculationRequest(listOf(outcome))
    val results = service.addDetailToCalculationResults(calculationRequest)
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
    val results = service.addDetailToCalculationResults(calculationRequest)
    assertThat(results.dates[type]?.hints).isEqualTo(
        listOf(
            ReleaseDateHint(
                "The Discretionary Friday/Pre-Bank Holiday Release Scheme Policy applies to this release date.",
                "https://www.gov.uk/government/publications/discretionary-fridaypre-bank-holiday-release-scheme-policy-framework",
            ),
        ),
    )
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
}