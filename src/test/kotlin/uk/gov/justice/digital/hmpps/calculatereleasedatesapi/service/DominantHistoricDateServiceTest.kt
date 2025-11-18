package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PreviouslyRecordedSLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import java.time.LocalDate

class DominantHistoricDateServiceTest {

  private val calculationOutcomeRepository = mock<CalculationOutcomeRepository>()
  private val service: DominantHistoricDateService = DominantHistoricDateService(calculationOutcomeRepository)

  @Test
  fun `should return null if no historic SLED or SED + LED exists`() {
    whenever(calculationOutcomeRepository.getPotentialDatesForPreviouslyRecordedSLED("A1234BC", CALCULATED_SLED)).thenReturn(emptyList())

    assertThat(service.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(PRISONER_ID, CALCULATED_SLED)).isNull()
  }

  @Test
  fun `should return a previously recorded SLED if a historic SLED exists`() {
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.SLED.name,
      ),
    )

    whenever(calculationOutcomeRepository.getPotentialDatesForPreviouslyRecordedSLED("A1234BC", CALCULATED_SLED)).thenReturn(outcomes)

    assertThat(service.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(PRISONER_ID, CALCULATED_SLED)).isEqualTo(
      PreviouslyRecordedSLED(
        previouslyRecordedSLEDDate = LocalDate.of(2021, 1, 1),
        calculatedDate = CALCULATED_SLED,
        previouslyRecordedSLEDCalculationRequestId = 1L,
      ),
    )
  }

  @Test
  fun `should return the latest previously recorded SLED if multiple historic SLEDs exist`() {
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.SLED.name,
      ),
      CalculationOutcome(
        calculationRequestId = 2L,
        outcomeDate = LocalDate.of(2021, 1, 2),
        calculationDateType = ReleaseDateType.LED.name,
      ),
      CalculationOutcome(
        calculationRequestId = 2L,
        outcomeDate = LocalDate.of(2021, 1, 2),
        calculationDateType = ReleaseDateType.SED.name,
      ),
    )

    whenever(calculationOutcomeRepository.getPotentialDatesForPreviouslyRecordedSLED("A1234BC", CALCULATED_SLED)).thenReturn(outcomes)

    assertThat(service.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(PRISONER_ID, CALCULATED_SLED)).isEqualTo(
      PreviouslyRecordedSLED(
        previouslyRecordedSLEDDate = LocalDate.of(2021, 1, 2),
        calculatedDate = CALCULATED_SLED,
        previouslyRecordedSLEDCalculationRequestId = 2L,
      ),
    )
  }

  @Test
  fun `should return a previously recorded SLED if a historic SED and LED with the same date exists`() {
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.SED.name,
      ),
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.LED.name,
      ),
    )

    whenever(calculationOutcomeRepository.getPotentialDatesForPreviouslyRecordedSLED("A1234BC", CALCULATED_SLED)).thenReturn(outcomes)

    assertThat(service.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(PRISONER_ID, CALCULATED_SLED)).isEqualTo(
      PreviouslyRecordedSLED(
        previouslyRecordedSLEDDate = LocalDate.of(2021, 1, 1),
        calculatedDate = CALCULATED_SLED,
        previouslyRecordedSLEDCalculationRequestId = 1L,
      ),
    )
  }

  @Test
  fun `should return null if a historic SED and LED exist but the dates are different`() {
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.SED.name,
      ),
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 2),
        calculationDateType = ReleaseDateType.LED.name,
      ),
    )

    whenever(calculationOutcomeRepository.getPotentialDatesForPreviouslyRecordedSLED("A1234BC", CALCULATED_SLED)).thenReturn(outcomes)

    assertThat(service.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(PRISONER_ID, CALCULATED_SLED)).isNull()
  }

  @Test
  fun `should return null if a historic SED and LED exist with the same date but from different calculations`() {
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.SED.name,
      ),
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 2),
        calculationDateType = ReleaseDateType.LED.name,
      ),
      CalculationOutcome(
        calculationRequestId = 2L,
        outcomeDate = LocalDate.of(2021, 1, 2),
        calculationDateType = ReleaseDateType.SED.name,
      ),
      CalculationOutcome(
        calculationRequestId = 2L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.LED.name,
      ),
    )

    whenever(calculationOutcomeRepository.getPotentialDatesForPreviouslyRecordedSLED("A1234BC", CALCULATED_SLED)).thenReturn(outcomes)

    assertThat(service.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(PRISONER_ID, CALCULATED_SLED)).isNull()
  }

  @Test
  fun `should return null if only a historic SED without LED exists`() {
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.SED.name,
      ),
    )

    whenever(calculationOutcomeRepository.getPotentialDatesForPreviouslyRecordedSLED("A1234BC", CALCULATED_SLED)).thenReturn(outcomes)

    assertThat(service.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(PRISONER_ID, CALCULATED_SLED)).isNull()
  }

  @Test
  fun `should return null if only a historic LED without SED exists`() {
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.LED.name,
      ),
    )

    whenever(calculationOutcomeRepository.getPotentialDatesForPreviouslyRecordedSLED("A1234BC", CALCULATED_SLED)).thenReturn(outcomes)

    assertThat(service.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(PRISONER_ID, CALCULATED_SLED)).isNull()
  }

  companion object {
    private const val PRISONER_ID = "A1234BC"
    private val CALCULATED_SLED = LocalDate.of(2021, 1, 2)
  }
}
