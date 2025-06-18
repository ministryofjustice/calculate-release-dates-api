package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate

class DominantHistoricDateServiceTest {

  private val service: DominantHistoricDateService = DominantHistoricDateService()

  @Test
  fun `should use calculated SLED if and no dominant historic LED or SLED exists`() {
    val calculatedSled = LocalDate.of(2021, 1, 2)
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.SLED.name,
      ),
      CalculationOutcome(
        calculationRequestId = 2L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.LED.name,
      ),
    )

    val overrides = service.calculateFromSled(calculatedSled, outcomes)

    assertEquals(calculatedSled, overrides[ReleaseDateType.SLED])
    assertFalse(overrides.containsKey(ReleaseDateType.LED))
    assertFalse(overrides.containsKey(ReleaseDateType.SED))
  }

  @Test
  fun `should use historic SLED if it exists and no newer LED or SED`() {
    val calculatedSled = LocalDate.of(2021, 1, 4)
    val historicSled = LocalDate.of(2021, 2, 1)
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = historicSled,
        calculationDateType = ReleaseDateType.SLED.name,
      ),
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.SED.name,
      ),
      CalculationOutcome(
        calculationRequestId = 2L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.LED.name,
      ),
    )

    val overrides = service.calculateFromSled(calculatedSled, outcomes)

    assertEquals(historicSled, overrides[ReleaseDateType.SLED])
    assertFalse(overrides.containsKey(ReleaseDateType.LED))
    assertFalse(overrides.containsKey(ReleaseDateType.SED))
  }

  @Test
  fun `should calculate LED and SED when historic LED is after historic SLED`() {
    val calculatedSled = LocalDate.of(2020, 2, 1)
    val historicSled = LocalDate.of(2021, 1, 1)
    val historicLed = LocalDate.of(2021, 2, 1)
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = historicSled,
        calculationDateType = ReleaseDateType.SLED.name,
      ),
      CalculationOutcome(
        calculationRequestId = 2L,
        outcomeDate = historicLed,
        calculationDateType = ReleaseDateType.LED.name,
      ),
    )

    val overrides = service.calculateFromSled(calculatedSled, outcomes)

    assertEquals(historicSled, overrides[ReleaseDateType.SED])
    assertEquals(historicLed, overrides[ReleaseDateType.LED])
    assertFalse(overrides.containsKey(ReleaseDateType.SLED))
  }

  @Test
  fun `should calculate LED and SED when historic SED is after historic LED`() {
    val calculatedSled = LocalDate.of(2020, 1, 1)
    val historicLed = LocalDate.of(2020, 5, 1)
    val historicSed = LocalDate.of(2021, 1, 1)
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 2L,
        outcomeDate = historicLed,
        calculationDateType = ReleaseDateType.LED.name,
      ),
      CalculationOutcome(
        calculationRequestId = 3L,
        outcomeDate = historicSed,
        calculationDateType = ReleaseDateType.SED.name,
      ),
    )

    val overrides = service.calculateFromSled(calculatedSled, outcomes)

    assertEquals(historicSed, overrides[ReleaseDateType.SED])
    assertEquals(historicLed, overrides[ReleaseDateType.LED])
    assertFalse(overrides.containsKey(ReleaseDateType.SLED))
  }

  @Test
  fun `should calculate LED and SED and return SLED if both dates are equal`() {
    val calculatedSled = LocalDate.of(2020, 1, 1)
    val historicLed = LocalDate.of(2021, 1, 1)
    val historicSed = LocalDate.of(2021, 1, 1)
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 2L,
        outcomeDate = historicLed,
        calculationDateType = ReleaseDateType.LED.name,
      ),
      CalculationOutcome(
        calculationRequestId = 3L,
        outcomeDate = historicSed,
        calculationDateType = ReleaseDateType.SED.name,
      ),
    )

    val overrides = service.calculateFromSled(calculatedSled, outcomes)

    assertEquals(overrides.containsKey(ReleaseDateType.SLED), true)
    assertEquals(historicLed, overrides[ReleaseDateType.SLED])
  }

  @Test
  fun `should use SED where no historic SLED exists`() {
    val calculatedSled = LocalDate.of(2022, 1, 1)
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.LED.name,
      ),
      CalculationOutcome(
        calculationRequestId = 2L,
        outcomeDate = LocalDate.of(2023, 1, 1),
        calculationDateType = ReleaseDateType.SED.name,
      ),
    )

    val overrides = service.calculateFromSled(calculatedSled, outcomes)

    assertEquals(LocalDate.of(2023, 1, 1), overrides[ReleaseDateType.SED])
    assertEquals(calculatedSled, overrides[ReleaseDateType.LED])
    assertFalse(overrides.containsKey(ReleaseDateType.SLED))
  }

  @Test
  fun `should use calculated SLED where no historic dates exists`() {
    val calculatedSled = LocalDate.of(2022, 1, 1)
    val outcomes = emptyList<CalculationOutcome>()
    val overrides = service.calculateFromSled(calculatedSled, outcomes)

    assertEquals(calculatedSled, overrides[ReleaseDateType.SLED])
  }

  @Test
  fun `latestDate returns the latest date`() {
    val base = LocalDate.of(2020, 1, 1)
    val newer = LocalDate.of(2021, 1, 1)

    assertEquals(newer, service.latestDate(base, newer))
    assertEquals(base, service.latestDate(base, null))
    assertEquals(base, service.latestDate(base, base.minusDays(1)))
  }
}
