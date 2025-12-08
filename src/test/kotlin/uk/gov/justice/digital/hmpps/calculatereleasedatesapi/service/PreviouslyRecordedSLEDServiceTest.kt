package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PreviouslyRecordedSLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.util.Optional

class PreviouslyRecordedSLEDServiceTest {

  private val calculationOutcomeRepository = mock<CalculationOutcomeRepository>()
  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val sourceDataMapper = mock<SourceDataMapper>()
  private val service: PreviouslyRecordedSLEDService = PreviouslyRecordedSLEDService(calculationOutcomeRepository, calculationRequestRepository, sourceDataMapper)

  @BeforeEach
  fun setUp() {
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CalculationRequest(id = 1L)))
    whenever(sourceDataMapper.mapAdjustments(any())).thenReturn(emptyList())
  }

  @Test
  fun `should return null if no historic SLED or SED + LED exists`() {
    whenever(calculationOutcomeRepository.getPotentialDatesForPreviouslyRecordedSLED("A1234BC", CALCULATED_SLED)).thenReturn(emptyList())

    assertThat(service.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(PRISONER_ID, CALCULATED_SLED)).isNull()
  }

  @Test
  fun `should return a previously recorded SLED if a historic SLED exists and there are no unused deductions`() {
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.SLED.name,
      ),
    )
    whenever(calculationOutcomeRepository.getPotentialDatesForPreviouslyRecordedSLED("A1234BC", CALCULATED_SLED)).thenReturn(outcomes)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CalculationRequest(id = 1L)))
    whenever(sourceDataMapper.mapAdjustments(any())).thenReturn(
      listOf(
        AdjustmentDto(
          bookingId = 1L,
          person = "A1234BC",
          adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
          days = 5,
        ),
      ),
    )

    assertThat(service.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(PRISONER_ID, CALCULATED_SLED)).isEqualTo(
      PreviouslyRecordedSLED(
        previouslyRecordedSLEDDate = LocalDate.of(2021, 1, 1),
        calculatedDate = CALCULATED_SLED,
        previouslyRecordedSLEDCalculationRequestId = 1L,
      ),
    )
  }

  @Test
  fun `should return a previously recorded SLED if a historic SLED exists and there are unused deductions but days are 0`() {
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.SLED.name,
      ),
    )
    whenever(calculationOutcomeRepository.getPotentialDatesForPreviouslyRecordedSLED("A1234BC", CALCULATED_SLED)).thenReturn(outcomes)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CalculationRequest(id = 1L)))
    whenever(sourceDataMapper.mapAdjustments(any())).thenReturn(
      listOf(
        AdjustmentDto(
          bookingId = 1L,
          person = "A1234BC",
          adjustmentType = AdjustmentDto.AdjustmentType.UNUSED_DEDUCTIONS,
          days = 0,
        ),
      ),
    )

    assertThat(service.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(PRISONER_ID, CALCULATED_SLED)).isEqualTo(
      PreviouslyRecordedSLED(
        previouslyRecordedSLEDDate = LocalDate.of(2021, 1, 1),
        calculatedDate = CALCULATED_SLED,
        previouslyRecordedSLEDCalculationRequestId = 1L,
      ),
    )
  }

  @Test
  fun `should not return a previously recorded SLED if the historic calculation had unused deductions`() {
    val outcomes = listOf(
      CalculationOutcome(
        calculationRequestId = 1L,
        outcomeDate = LocalDate.of(2021, 1, 1),
        calculationDateType = ReleaseDateType.SLED.name,
      ),
    )

    whenever(calculationOutcomeRepository.getPotentialDatesForPreviouslyRecordedSLED("A1234BC", CALCULATED_SLED)).thenReturn(outcomes)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CalculationRequest(id = 1L)))
    whenever(sourceDataMapper.mapAdjustments(any())).thenReturn(
      listOf(
        AdjustmentDto(
          bookingId = 1L,
          person = "A1234BC",
          adjustmentType = AdjustmentDto.AdjustmentType.UNUSED_DEDUCTIONS,
          days = 1,
        ),
      ),
    )

    assertThat(service.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(PRISONER_ID, CALCULATED_SLED)).isNull()
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
