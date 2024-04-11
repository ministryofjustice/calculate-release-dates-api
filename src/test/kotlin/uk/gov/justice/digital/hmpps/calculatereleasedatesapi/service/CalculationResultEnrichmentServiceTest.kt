package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NonFridayReleaseDay
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateHint
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.WorkingDay
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class CalculationResultEnrichmentServiceTest {

  private val nonFridayReleaseService = mock<NonFridayReleaseService>()
  private val workingDayService = mock<WorkingDayService>()

  @Test
  fun `should add the full release date type name for all release dates`() {
    val sedDate = LocalDate.of(2021, 2, 3)
    val crdDate = LocalDate.of(2022, 3, 4)

    whenever(nonFridayReleaseService.getDate(ReleaseDate(sedDate, ReleaseDateType.SED))).thenReturn(NonFridayReleaseDay(sedDate, false))
    whenever(nonFridayReleaseService.getDate(ReleaseDate(crdDate, ReleaseDateType.CRD))).thenReturn(NonFridayReleaseDay(crdDate, false))
    whenever(workingDayService.previousWorkingDay(sedDate)).thenReturn(WorkingDay(sedDate, adjustedForWeekend = false, adjustedForBankHoliday = false))
    whenever(workingDayService.previousWorkingDay(crdDate)).thenReturn(WorkingDay(crdDate, adjustedForWeekend = false, adjustedForBankHoliday = false))

    val sedReleaseDate = ReleaseDate(sedDate, ReleaseDateType.SED)
    val crdReleaseDate = ReleaseDate(crdDate, ReleaseDateType.CRD)
    val releaseDates = listOf(sedReleaseDate, crdReleaseDate)
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, CalculationBreakdown(emptyList(), null))
    assertThat(results).isEqualTo(
      mapOf(
        ReleaseDateType.SED to DetailedDate(ReleaseDateType.SED, "Sentence expiry date", sedDate, emptyList()),
        ReleaseDateType.CRD to DetailedDate(ReleaseDateType.CRD, "Conditional release date", crdDate, emptyList()),
      ),
    )
  }

  @ParameterizedTest
  @EnumSource(ReleaseDateType::class)
  fun `every release date type has a description`(type: ReleaseDateType) {
    val date = LocalDate.of(2021, 2, 3)
    whenever(nonFridayReleaseService.getDate(ReleaseDate(date, type))).thenReturn(NonFridayReleaseDay(date, false))
    whenever(workingDayService.previousWorkingDay(date)).thenReturn(WorkingDay(date, adjustedForWeekend = false, adjustedForBankHoliday = false))
    whenever(workingDayService.nextWorkingDay(date)).thenReturn(WorkingDay(date, adjustedForWeekend = false, adjustedForBankHoliday = false))
    val releaseDate = ReleaseDate(date, type)
    val releaseDates = listOf(releaseDate)
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, CalculationBreakdown(emptyList(), null))
    assertThat(results[type]?.description).isNotBlank()
  }

  @Test
  fun `should calculate non friday release date adjustments for relevant dates`() {
    val type = ReleaseDateType.CRD
    val originalDate = LocalDate.of(2021, 2, 3)
    val adjustedDate = LocalDate.of(2021, 2, 1)

    whenever(nonFridayReleaseService.getDate(ReleaseDate(originalDate, type))).thenReturn(NonFridayReleaseDay(adjustedDate, true))

    val releaseDate = ReleaseDate(originalDate, type)
    val releaseDates = listOf(releaseDate)
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, CalculationBreakdown(emptyList(), null))
    assertThat(results[type]?.hints).isEqualTo(
      listOf(
        ReleaseDateHint(
          "The Discretionary Friday/Pre-Bank Holiday Release Scheme Policy applies to this release date.",
          "https://www.gov.uk/government/publications/discretionary-fridaypre-bank-holiday-release-scheme-policy-framework",
        ),
      ),
    )
    // Only checks weekend if non-working day doesn't apply
    verify(workingDayService, never()).previousWorkingDay(any())
  }

  @ParameterizedTest
  @CsvSource(
    "CRD,true",
    "ARD,true",
    "PRRD,true",
    "PED,true",
    "ETD,true",
    "MTD,true",
    "LTD,true",
    "LED,false",
  )
  fun `should calculate weekend adjustments with previous working day for relevant dates`(type: ReleaseDateType, expected: Boolean) {
    val originalDate = LocalDate.of(2021, 2, 6)
    val adjustedDate = LocalDate.of(2021, 2, 5)

    whenever(nonFridayReleaseService.getDate(ReleaseDate(originalDate, type))).thenReturn(NonFridayReleaseDay(originalDate, false))
    whenever(workingDayService.previousWorkingDay(originalDate)).thenReturn(WorkingDay(adjustedDate, adjustedForWeekend = false, adjustedForBankHoliday = false))

    val releaseDate = ReleaseDate(originalDate, type)
    val releaseDates = listOf(releaseDate)
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, CalculationBreakdown(emptyList(), null))
    assertThat(results[type]?.hints).isEqualTo(
      if (expected) {
        listOf(ReleaseDateHint("Friday, 05 February 2021 when adjusted to a working day"))
      } else {
        emptyList()
      },
    )
  }

  @Test
  fun `should calculate weekend adjustments as next working day for HDCED`() {
    val originalDate = LocalDate.of(2021, 2, 6)
    val adjustedDate = LocalDate.of(2021, 2, 8)
    val type = ReleaseDateType.HDCED
    whenever(nonFridayReleaseService.getDate(ReleaseDate(originalDate, type))).thenReturn(NonFridayReleaseDay(originalDate, false))
    whenever(workingDayService.nextWorkingDay(originalDate)).thenReturn(WorkingDay(adjustedDate, adjustedForWeekend = false, adjustedForBankHoliday = false))

    val releaseDate = ReleaseDate(originalDate, type)
    val releaseDates = listOf(releaseDate)
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, CalculationBreakdown(emptyList(), null))
    assertThat(results[type]?.hints).isEqualTo(
      listOf(ReleaseDateHint("Monday, 08 February 2021 when adjusted to a working day")),
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

    val releaseDate = ReleaseDate(originalDate, type)
    val releaseDates = listOf(releaseDate)
    val results = calculationResultEnrichmentService(today).addDetailToCalculationDates(releaseDates, null, CalculationBreakdown(emptyList(), null))
    assertThat(results[type]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should add DTO later than ARD hint if has DTO and non DTO sentence and MTD is after ARD`() {
    val (ardDate, ardType) = getReleaseDateAndStubAdjustments(ReleaseDateType.ARD, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, ardDate.plusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(ardDate, ardType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[ardType]?.hints).isEqualTo(listOf(ReleaseDateHint("The Detention and training order (DTO) release date is later than the Automatic Release Date (ARD)")))
  }

  @Test
  fun `should not add DTO later than ARD hint if has DTO and non DTO sentence if there is no MTD date`() {
    val (ardDate, ardType) = getReleaseDateAndStubAdjustments(ReleaseDateType.ARD, LocalDate.of(2021, 2, 3))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(ardDate, ardType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[ardType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should not add DTO later than ARD hint if has DTO and no non-DTO sentence and MTD is after ARD`() {
    val (ardDate, ardType) = getReleaseDateAndStubAdjustments(ReleaseDateType.ARD, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, ardDate.plusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
    )
    val releaseDates = listOf(ReleaseDate(ardDate, ardType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[ardType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should not add DTO later than ARD hint if has no DTO but no non-DTO sentence and MTD is after ARD`() {
    val (ardDate, ardType) = getReleaseDateAndStubAdjustments(ReleaseDateType.ARD, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, ardDate.plusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(ardDate, ardType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[ardType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should not add DTO later than ARD hint if has DTO and a non-DTO sentence but MTD is before ARD`() {
    val (ardDate, ardType) = getReleaseDateAndStubAdjustments(ReleaseDateType.ARD, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, ardDate.minusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(ardDate, ardType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[ardType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should add DTO later than CRD hint if has DTO and non DTO sentence and MTD is after CRD`() {
    val (crdDate, crdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.CRD, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, crdDate.plusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(crdDate, crdType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[crdType]?.hints).isEqualTo(listOf(ReleaseDateHint("The Detention and training order (DTO) release date is later than the Conditional Release Date (CRD)")))
  }

  @Test
  fun `should not add DTO later than CRD hint if has DTO and non DTO sentence if there is no MTD date`() {
    val (crdDate, crdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.CRD, LocalDate.of(2021, 2, 3))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(crdDate, crdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[crdType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should not add DTO later than CRD hint if has DTO and no non-DTO sentence and MTD is after CRD`() {
    val (crdDate, crdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.CRD, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, crdDate.plusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
    )
    val releaseDates = listOf(ReleaseDate(crdDate, crdType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[crdType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should not add DTO later than CRD hint if has no DTO but no non-DTO sentence and MTD is after CRD`() {
    val (crdDate, crdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.CRD, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, crdDate.plusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(crdDate, crdType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[crdType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should not add DTO later than CRD hint if has DTO and a non-DTO sentence but MTD is before CRD`() {
    val (crdDate, crdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.CRD, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, crdDate.minusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(crdDate, crdType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[crdType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should add DTO later than PED hint if has DTO and non DTO sentence and MTD is after PED`() {
    val (pedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.PED, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, pedDate.plusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(pedDate, pedType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[pedType]?.hints).isEqualTo(listOf(ReleaseDateHint("The Detention and training order (DTO) release date is later than the Parole Eligibility Date (PED)")))
  }

  @Test
  fun `should not add DTO later than PED hint if has DTO and non DTO sentence if there is no MTD date`() {
    val (pedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.PED, LocalDate.of(2021, 2, 3))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(pedDate, pedType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[pedType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should not add DTO later than PED hint if has DTO and no non-DTO sentence and MTD is after PED`() {
    val (pedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.PED, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, pedDate.plusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
    )
    val releaseDates = listOf(ReleaseDate(pedDate, pedType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[pedType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should not add DTO later than PED hint if has no DTO but no non-DTO sentence and MTD is after PED`() {
    val (pedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.PED, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, pedDate.plusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(pedDate, pedType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[pedType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should not add DTO later than PED hint if has DTO and a non-DTO sentence but MTD is before PED`() {
    val (pedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.PED, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, pedDate.minusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(pedDate, pedType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[pedType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @ParameterizedTest
  @CsvSource(
    "CRD,PED_EQUAL_TO_LATEST_NON_PED_CONDITIONAL_RELEASE",
    "ARD,PED_EQUAL_TO_LATEST_NON_PED_ACTUAL_RELEASE",
  )
  fun `should add PED adjustment hint for CRD or ARD`(releaseDateType: ReleaseDateType, rule: CalculationRule) {
    val (pedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.PED, LocalDate.of(2021, 2, 3))

    val releaseDates = listOf(ReleaseDate(pedDate, pedType))
    val calculationBreakdown = CalculationBreakdown(
      emptyList(),
      null,
      mapOf(
        ReleaseDateType.PED to ReleaseDateCalculationBreakdown(
          setOf(rule),
        ),
      ),
    )
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, calculationBreakdown)
    assertThat(results[pedType]?.hints).isEqualTo(listOf(ReleaseDateHint("PED adjusted for the ${releaseDateType.name} of a concurrent sentence or default term")))
  }

  @Test
  fun `should add PED adjustment hint for CRD if rules for CRD and ARD are both present`() {
    val (pedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.PED, LocalDate.of(2021, 2, 3))

    val releaseDates = listOf(ReleaseDate(pedDate, pedType))
    val calculationBreakdown = CalculationBreakdown(
      emptyList(),
      null,
      mapOf(
        ReleaseDateType.PED to ReleaseDateCalculationBreakdown(
          setOf(CalculationRule.PED_EQUAL_TO_LATEST_NON_PED_CONDITIONAL_RELEASE, CalculationRule.PED_EQUAL_TO_LATEST_NON_PED_ACTUAL_RELEASE),
        ),
      ),
    )
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, calculationBreakdown)
    assertThat(results[pedType]?.hints).isEqualTo(listOf(ReleaseDateHint("PED adjusted for the CRD of a concurrent sentence or default term")))
  }

  @Test
  fun `should add PED before PRRD hint if PRRD is present in other dates and is after PED`() {
    val (pedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.PED, LocalDate.of(2021, 2, 1))

    val releaseDates = listOf(ReleaseDate(pedDate, pedType))
    val calculationBreakdown = CalculationBreakdown(
      emptyList(),
      null,
      emptyMap(),
      mapOf(ReleaseDateType.PRRD to LocalDate.of(2021, 2, 2)),
    )
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, calculationBreakdown)
    assertThat(results[pedType]?.hints).isEqualTo(listOf(ReleaseDateHint("The post recall release date (PRRD) of Tuesday, 02 February 2021 is later than the PED")))
  }

  @Test
  fun `should not add PED before PRRD hint if PRRD is present in other dates but is before PED`() {
    val (pedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.PED, LocalDate.of(2021, 2, 1))

    val releaseDates = listOf(ReleaseDate(pedDate, pedType))
    val calculationBreakdown = CalculationBreakdown(
      emptyList(),
      null,
      emptyMap(),
      mapOf(ReleaseDateType.PRRD to LocalDate.of(2021, 2, 1)),
    )
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, calculationBreakdown)
    assertThat(results[pedType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should add all PED hints if they all apply`() {
    val (pedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.PED, LocalDate.of(2021, 2, 1))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, pedDate.plusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(pedDate, pedType), ReleaseDate(mtdDate, mtdType))
    val calculationBreakdown = CalculationBreakdown(
      emptyList(),
      null,
      mapOf(ReleaseDateType.PED to ReleaseDateCalculationBreakdown(setOf(CalculationRule.PED_EQUAL_TO_LATEST_NON_PED_CONDITIONAL_RELEASE))),
      mapOf(ReleaseDateType.PRRD to LocalDate.of(2021, 2, 2)),
    )
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, calculationBreakdown)
    assertThat(results[pedType]?.hints).isEqualTo(
      listOf(
        ReleaseDateHint("PED adjusted for the CRD of a concurrent sentence or default term"),
        ReleaseDateHint("The post recall release date (PRRD) of Tuesday, 02 February 2021 is later than the PED"),
        ReleaseDateHint("The Detention and training order (DTO) release date is later than the Parole Eligibility Date (PED)"),
      ),
    )
  }

  @Test
  fun `should add DTO later than HDCED hint if has DTO and non DTO sentence and MTD is after HDCED`() {
    val (hdcedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.HDCED, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, hdcedDate.plusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(hdcedDate, pedType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[pedType]?.hints).isEqualTo(listOf(ReleaseDateHint("The Detention and training order (DTO) release date is later than the Home detention curfew eligibility date (HDCED)")))
  }

  @Test
  fun `should not add DTO later than HDCED hint if has DTO and non DTO sentence if there is no MTD date`() {
    val (hdcedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.HDCED, LocalDate.of(2021, 2, 3))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(hdcedDate, pedType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[pedType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should not add DTO later than HDCED hint if has DTO and no non-DTO sentence and MTD is after HDCED`() {
    val (hdcedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.HDCED, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, hdcedDate.plusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
    )
    val releaseDates = listOf(ReleaseDate(hdcedDate, pedType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[pedType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should not add DTO later than HDCED hint if has no DTO but no non-DTO sentence and MTD is after HDCED`() {
    val (hdcedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.HDCED, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, hdcedDate.plusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(hdcedDate, pedType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[pedType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should not add DTO later than HDCED hint if has DTO and a non-DTO sentence but MTD is before HDCED`() {
    val (hdcedDate, hdcedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.HDCED, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, hdcedDate.minusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(hdcedDate, hdcedType), ReleaseDate(mtdDate, mtdType))
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, CalculationBreakdown(emptyList(), null))
    assertThat(results[hdcedType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @ParameterizedTest
  @CsvSource(
    "CRD,HDCED_ADJUSTED_TO_CONCURRENT_CONDITIONAL_RELEASE",
    "ARD,HDCED_ADJUSTED_TO_CONCURRENT_ACTUAL_RELEASE",
  )
  fun `should add HDCED adjustment hint for CRD or ARD`(releaseDateType: ReleaseDateType, rule: CalculationRule) {
    val (hdcedDate, hdcedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.HDCED, LocalDate.of(2021, 2, 3))

    val releaseDates = listOf(ReleaseDate(hdcedDate, hdcedType))
    val calculationBreakdown = CalculationBreakdown(
      emptyList(),
      null,
      mapOf(
        ReleaseDateType.HDCED to ReleaseDateCalculationBreakdown(
          setOf(rule),
        ),
      ),
    )
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, calculationBreakdown)
    assertThat(results[hdcedType]?.hints).isEqualTo(listOf(ReleaseDateHint("HDCED adjusted for the ${releaseDateType.name} of a concurrent sentence or default term")))
  }

  @Test
  fun `should add HDCED adjustment hint for CRD if rules for CRD and ARD are both present`() {
    val (hdcedDate, hdcedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.HDCED, LocalDate.of(2021, 2, 3))

    val releaseDates = listOf(ReleaseDate(hdcedDate, hdcedType))
    val calculationBreakdown = CalculationBreakdown(
      emptyList(),
      null,
      mapOf(
        ReleaseDateType.HDCED to ReleaseDateCalculationBreakdown(
          setOf(CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_CONDITIONAL_RELEASE, CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_ACTUAL_RELEASE),
        ),
      ),
    )
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, calculationBreakdown)
    assertThat(results[hdcedType]?.hints).isEqualTo(listOf(ReleaseDateHint("HDCED adjusted for the CRD of a concurrent sentence or default term")))
  }

  @Test
  fun `should add HDCED before PRRD hint if PRRD is present in other dates and is after HDCED`() {
    val (hdcedDate, hdcedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.HDCED, LocalDate.of(2021, 2, 1))

    val releaseDates = listOf(ReleaseDate(hdcedDate, hdcedType))
    val calculationBreakdown = CalculationBreakdown(
      emptyList(),
      null,
      emptyMap(),
      mapOf(ReleaseDateType.PRRD to LocalDate.of(2021, 2, 2)),
    )
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, calculationBreakdown)
    assertThat(results[hdcedType]?.hints).isEqualTo(listOf(ReleaseDateHint("Release on HDC must not take place before the PRRD Tuesday, 02 February 2021")))
  }

  @Test
  fun `should not add HDCED before PRRD hint if PRRD is present in other dates but is before HDCED`() {
    val (hdcedDate, hdcedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.HDCED, LocalDate.of(2021, 2, 1))

    val releaseDates = listOf(ReleaseDate(hdcedDate, hdcedType))
    val calculationBreakdown = CalculationBreakdown(
      emptyList(),
      null,
      emptyMap(),
      mapOf(ReleaseDateType.PRRD to LocalDate.of(2021, 2, 1)),
    )
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, calculationBreakdown)
    assertThat(results[hdcedType]?.hints).isEqualTo(emptyList<ReleaseDateHint>())
  }

  @Test
  fun `should add all HDCED hints if they all apply`() {
    val (hdcedDate, hdcedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.HDCED, LocalDate.of(2021, 2, 1))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, hdcedDate.plusDays(1))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(hdcedDate, hdcedType), ReleaseDate(mtdDate, mtdType))
    val calculationBreakdown = CalculationBreakdown(
      emptyList(),
      null,
      mapOf(ReleaseDateType.HDCED to ReleaseDateCalculationBreakdown(setOf(CalculationRule.HDCED_ADJUSTED_TO_CONCURRENT_CONDITIONAL_RELEASE))),
      mapOf(ReleaseDateType.PRRD to LocalDate.of(2021, 2, 2)),
    )
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, calculationBreakdown)
    assertThat(results[hdcedType]?.hints).isEqualTo(
      listOf(
        ReleaseDateHint("HDCED adjusted for the CRD of a concurrent sentence or default term"),
        ReleaseDateHint("Release on HDC must not take place before the PRRD Tuesday, 02 February 2021"),
        ReleaseDateHint("The Detention and training order (DTO) release date is later than the Home detention curfew eligibility date (HDCED)"),
      ),
    )
  }

  @Test
  fun `should add MTD hint if HDCED before MTD and MTD before CRD`() {
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, LocalDate.of(2021, 1, 3))
    val (hdcedDate, hdcedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.HDCED, LocalDate.of(2021, 1, 1))
    val (crdDate, crdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.CRD, LocalDate.of(2021, 1, 5))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(mtdDate, mtdType), ReleaseDate(hdcedDate, hdcedType), ReleaseDate(crdDate, crdType))
    val calculationBreakdown = CalculationBreakdown(emptyList(), null)
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, calculationBreakdown)
    assertThat(results[mtdType]?.hints).isEqualTo(
      listOf(ReleaseDateHint("Release from Detention and training order (DTO) cannot happen until release from the sentence (earliest would be the Conditional release date)")),
    )
  }

  @Test
  fun `should add MTD hint if PED before MTD and MTD before CRD`() {
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, LocalDate.of(2021, 1, 3))
    val (pedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.PED, LocalDate.of(2021, 1, 1))
    val (crdDate, crdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.CRD, LocalDate.of(2021, 1, 5))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(mtdDate, mtdType), ReleaseDate(pedDate, pedType), ReleaseDate(crdDate, crdType))
    val calculationBreakdown = CalculationBreakdown(emptyList(), null)
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, calculationBreakdown)
    assertThat(results[mtdType]?.hints).isEqualTo(
      listOf(ReleaseDateHint("Release from Detention and training order (DTO) cannot happen until release from the sentence (earliest would be the Conditional release date)")),
    )
  }

  @Test
  fun `should add MTD hint if HDCED before MTD and MTD before ARD`() {
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, LocalDate.of(2021, 1, 3))
    val (hdcedDate, hdcedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.HDCED, LocalDate.of(2021, 1, 1))
    val (ardDate, ardType) = getReleaseDateAndStubAdjustments(ReleaseDateType.ARD, LocalDate.of(2021, 1, 5))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(mtdDate, mtdType), ReleaseDate(hdcedDate, hdcedType), ReleaseDate(ardDate, ardType))
    val calculationBreakdown = CalculationBreakdown(emptyList(), null)
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, calculationBreakdown)
    assertThat(results[mtdType]?.hints).isEqualTo(
      listOf(ReleaseDateHint("Release from Detention and training order (DTO) cannot happen until release from the sentence (earliest would be the Automatic release date)")),
    )
  }

  @Test
  fun `should add MTD hint if PED before MTD and MTD before ARD`() {
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, LocalDate.of(2021, 1, 3))
    val (pedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.PED, LocalDate.of(2021, 1, 1))
    val (ardDate, ardType) = getReleaseDateAndStubAdjustments(ReleaseDateType.ARD, LocalDate.of(2021, 1, 5))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(mtdDate, mtdType), ReleaseDate(pedDate, pedType), ReleaseDate(ardDate, ardType))
    val calculationBreakdown = CalculationBreakdown(emptyList(), null)
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, calculationBreakdown)
    assertThat(results[mtdType]?.hints).isEqualTo(
      listOf(ReleaseDateHint("Release from Detention and training order (DTO) cannot happen until release from the sentence (earliest would be the Automatic release date)")),
    )
  }

  @Test
  fun `should add MTD hint if MTD before HDCED and HDCED before CRD`() {
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, LocalDate.of(2021, 1, 3))
    val (hdcedDate, hdcedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.HDCED, LocalDate.of(2021, 1, 5))
    val (crdDate, crdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.CRD, LocalDate.of(2021, 1, 7))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(mtdDate, mtdType), ReleaseDate(hdcedDate, hdcedType), ReleaseDate(crdDate, crdType))
    val calculationBreakdown = CalculationBreakdown(emptyList(), null)
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, calculationBreakdown)
    assertThat(results[mtdType]?.hints).isEqualTo(
      listOf(ReleaseDateHint("Release from the Detention and training order (DTO) cannot happen until release from the sentence (earliest would be the Home Detention Curfew Eligibility Date)")),
    )
  }

  @Test
  fun `should add MTD hint if MTD before HDCED and HDCED before ARD`() {
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, LocalDate.of(2021, 1, 3))
    val (hdcedDate, hdcedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.HDCED, LocalDate.of(2021, 1, 5))
    val (ardDate, ardType) = getReleaseDateAndStubAdjustments(ReleaseDateType.ARD, LocalDate.of(2021, 1, 7))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(mtdDate, mtdType), ReleaseDate(hdcedDate, hdcedType), ReleaseDate(ardDate, ardType))
    val calculationBreakdown = CalculationBreakdown(emptyList(), null)
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, calculationBreakdown)
    assertThat(results[mtdType]?.hints).isEqualTo(
      listOf(ReleaseDateHint("Release from the Detention and training order (DTO) cannot happen until release from the sentence (earliest would be the Home Detention Curfew Eligibility Date)")),
    )
  }

  @Test
  fun `should add MTD hint if MTD before PED and PED before CRD`() {
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, LocalDate.of(2021, 1, 3))
    val (pedDate, pedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.PED, LocalDate.of(2021, 1, 5))
    val (crdDate, crdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.CRD, LocalDate.of(2021, 1, 7))

    val sentencesAndOffences = listOf(
      sentenceAndOffence("DTO"),
      sentenceAndOffence("LR"),
    )
    val releaseDates = listOf(ReleaseDate(mtdDate, mtdType), ReleaseDate(pedDate, pedType), ReleaseDate(crdDate, crdType))
    val calculationBreakdown = CalculationBreakdown(emptyList(), null)
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, sentencesAndOffences, calculationBreakdown)
    assertThat(results[mtdType]?.hints).isEqualTo(
      listOf(ReleaseDateHint("Release from Detention and training order (DTO) cannot happen until release from the sentence (earliest would be the Parole Eligibility Date)")),
    )
  }

  @Test
  fun `should add ERSED adjusted for the ARD of a concurrent default term`() {
    val (ersedDate, ersedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.ERSED, LocalDate.of(2021, 2, 3))

    val releaseDates = listOf(ReleaseDate(ersedDate, ersedType))
    val calculationBreakdown = CalculationBreakdown(
      emptyList(),
      null,
      mapOf(ReleaseDateType.ERSED to ReleaseDateCalculationBreakdown(setOf(CalculationRule.ERSED_ADJUSTED_TO_CONCURRENT_TERM))),
    )
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, calculationBreakdown)
    assertThat(results[ersedType]?.hints).isEqualTo(listOf(ReleaseDateHint("ERSED adjusted for the ARD of a concurrent default term")))
  }

  @Test
  fun `should add ERSED adjusted to MTD of the DTO hint`() {
    val (ersedDate, ersedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.ERSED, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, LocalDate.of(2021, 2, 5))
    val (crdDate, crdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.CRD, LocalDate.of(2021, 2, 7))

    val releaseDates = listOf(ReleaseDate(ersedDate, ersedType), ReleaseDate(mtdDate, mtdType), ReleaseDate(crdDate, crdType))
    val calculationBreakdown = CalculationBreakdown(emptyList(), null)
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, calculationBreakdown)
    assertThat(results[ersedType]?.hints).isEqualTo(listOf(ReleaseDateHint("Adjusted to Mid term date (MTD) of the Detention and training order (DTO)")))
  }

  @Test
  fun `should add all applicable ERSED hints`() {
    val (ersedDate, ersedType) = getReleaseDateAndStubAdjustments(ReleaseDateType.ERSED, LocalDate.of(2021, 2, 3))
    val (mtdDate, mtdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.MTD, LocalDate.of(2021, 2, 5))
    val (crdDate, crdType) = getReleaseDateAndStubAdjustments(ReleaseDateType.CRD, LocalDate.of(2021, 2, 7))

    val releaseDates = listOf(ReleaseDate(ersedDate, ersedType), ReleaseDate(mtdDate, mtdType), ReleaseDate(crdDate, crdType))
    val calculationBreakdown = CalculationBreakdown(
      emptyList(),
      null,
      mapOf(ReleaseDateType.ERSED to ReleaseDateCalculationBreakdown(setOf(CalculationRule.ERSED_ADJUSTED_TO_CONCURRENT_TERM))),
    )
    val results = calculationResultEnrichmentService().addDetailToCalculationDates(releaseDates, null, calculationBreakdown)
    assertThat(results[ersedType]?.hints).isEqualTo(
      listOf(
        ReleaseDateHint("ERSED adjusted for the ARD of a concurrent default term"),
        ReleaseDateHint("Adjusted to Mid term date (MTD) of the Detention and training order (DTO)"),
      ),
    )
  }

  private fun getReleaseDateAndStubAdjustments(type: ReleaseDateType, date: LocalDate): ReleaseDate {
    whenever(nonFridayReleaseService.getDate(ReleaseDate(date, type))).thenReturn(NonFridayReleaseDay(date, false))
    whenever(workingDayService.previousWorkingDay(date)).thenReturn(WorkingDay(date, adjustedForWeekend = false, adjustedForBankHoliday = false))
    whenever(workingDayService.nextWorkingDay(date)).thenReturn(WorkingDay(date, adjustedForWeekend = false, adjustedForBankHoliday = false))
    return ReleaseDate(date, type)
  }

  private fun sentenceAndOffence(sentenceCalculationType: String, sentenceDate: LocalDate = LocalDate.of(2020, 1, 2), bookingId: Long = 0, sentenceSequence: Int = 0, lineSequence: Int = 0, sentenceTerm: Int = 5) = SentenceAndOffences(
    bookingId = bookingId,
    sentenceSequence = sentenceSequence,
    lineSequence = lineSequence,
    caseSequence = 1,
    sentenceDate = sentenceDate,
    terms = listOf(
      SentenceTerms(years = sentenceTerm),
    ),
    sentenceStatus = "A",
    sentenceCategory = "SEN",
    sentenceCalculationType = sentenceCalculationType,
    sentenceTypeDescription = "DESC",
    offences = listOf(offenderOffence),
  )

  private val offenderOffence = OffenderOffence(
    123,
    LocalDate.of(2012, 1, 1),
    LocalDate.of(2012, 1, 1),
    "AB123DEF",
    "finagling",
    emptyList(),
  )

  private fun calculationResultEnrichmentService(today: LocalDate = LocalDate.of(2000, 1, 1)): CalculationResultEnrichmentService {
    val clock = Clock.fixed(today.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
    return CalculationResultEnrichmentService(nonFridayReleaseService, workingDayService, clock)
  }
}
