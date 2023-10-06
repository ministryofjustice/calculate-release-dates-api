package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.GenuineOverride
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CalculationNotFoundException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.GenuineOverrideRepository
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class GenuineOverrideServiceTest() {
  private val genuineOverrideRepository = mock<GenuineOverrideRepository>()
  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val manualCalculationService = mock<ManualCalculationService>()
  private val underTest = GenuineOverrideService(genuineOverrideRepository, calculationRequestRepository, manualCalculationService)

  @Nested
  inner class CreateGenuineOverride {
    @Test
    fun `Should save genuine override if calculation is found`() {
      whenever(calculationRequestRepository.findByCalculationReference(any())).thenReturn(Optional.of(CALCULATION_REQUEST))
      whenever(genuineOverrideRepository.save(any())).thenReturn(GENUINE_OVERRIDE)

      val result = underTest.createGenuineOverride(
        GenuineOverrideRequest(
          reason = "A reason",
          originalCalculationRequest = "9c406dc1-2570-413b-8a72-f3ecbe03d986",
          isOverridden = false,
          savedCalculation = null,
        ),
      )
      assertThat(result).isNotNull
    }

    @Test
    fun `Will throw an exception if the original calculation reference isn't found`() {
      whenever(calculationRequestRepository.findByCalculationReference(any())).thenReturn(Optional.empty())
      val exception = assertThrows<CalculationNotFoundException> {
        underTest.createGenuineOverride(
          GenuineOverrideRequest(
            reason = "A reason",
            originalCalculationRequest = "9c406dc1-2570-413b-8a72-f3ecbe03d986",
            isOverridden = false,
            savedCalculation = null,
          ),
        )
      }
      assertThat(exception)
        .isInstanceOf(CalculationNotFoundException::class.java)
        .withFailMessage("Could not find original calculation with reference 9c406dc1-2570-413b-8a72-f3ecbe03d986")
    }
  }

  companion object {
    private val CALCULATION_REFERENCE: UUID = UUID.fromString("219db65e-d7b7-4c70-9239-98babff7bcd5")
    private const val PRISONER_ID = "A1234AJ"
    private const val BOOKING_ID = 12345L
    private val CALCULATION_REQUEST = CalculationRequest(
      calculationReference = CALCULATION_REFERENCE,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING_ID,
    )
    private val GENUINE_OVERRIDE = GenuineOverride(
      id = 1L,
      reason = "A reason",
      originalCalculationRequest = CALCULATION_REQUEST,
      savedCalculation = null,
      isOverridden = false,
      savedAt = LocalDateTime.now(),
    )
  }
}
