package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.AdjustmentEffectiveDays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.AdjustmentServiceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.AdjustmentServiceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.AdjustmentsApiClient
import java.time.LocalDate
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class UnusedDeductionsServiceTest {

  @InjectMocks
  lateinit var unusedDeductionsService: UnusedDeductionsService
  private val adjustmentsApiClient = mock<AdjustmentsApiClient>()
  private val unusedDeductionsCalculationService = mock<UnusedDeductionsCalculationService>()

  @Test
  fun updateUnusedDeductions() {
    val person = "ABC123"
    val remand = AdjustmentServiceAdjustment(
      UUID.randomUUID(), 1, 1, person, AdjustmentServiceAdjustmentType.REMAND, LocalDate.now().minusDays(100),
      LocalDate.now().minusDays(9), null, 90, 90,
    )
    val taggedBail = remand.copy(id = UUID.randomUUID(), adjustmentType = AdjustmentServiceAdjustmentType.TAGGED_BAIL, days = 90, daysBetween = null)
    val unusedDeductions = remand.copy(id = UUID.randomUUID(), adjustmentType = AdjustmentServiceAdjustmentType.UNUSED_DEDUCTIONS, days = 10, effectiveDays = 10, daysBetween = null)
    val adjustments = listOf(remand, taggedBail, unusedDeductions)

    whenever(adjustmentsApiClient.getAdjustmentsByPerson(person)).thenReturn(adjustments)
    whenever(unusedDeductionsCalculationService.calculate(adjustments, person)).thenReturn(100)

    unusedDeductionsService.handleUnusedDeductionRequest(person)

    verify(adjustmentsApiClient).updateEffectiveDays(AdjustmentEffectiveDays(taggedBail.id!!, 0, person))
    verify(adjustmentsApiClient).updateEffectiveDays(AdjustmentEffectiveDays(remand.id!!, 80, person))
    verify(adjustmentsApiClient).updateAdjustment(unusedDeductions.copy(days = 100))
  }

  @Test
  fun updateUnusedDeductions_noExistingUnusedDeductionAdjustment() {
    val person = "ABC123"
    val remand = AdjustmentServiceAdjustment(
      UUID.randomUUID(), 1, 1, person, AdjustmentServiceAdjustmentType.REMAND, LocalDate.now().minusDays(100),
      LocalDate.now().minusDays(9), null, 90, 90,
    )
    val taggedBail = remand.copy(id = UUID.randomUUID(), adjustmentType = AdjustmentServiceAdjustmentType.TAGGED_BAIL, days = 90, daysBetween = null)
    val adjustments = listOf(remand, taggedBail)

    whenever(adjustmentsApiClient.getAdjustmentsByPerson(person)).thenReturn(adjustments)
    whenever(unusedDeductionsCalculationService.calculate(adjustments, person)).thenReturn(100)

    unusedDeductionsService.handleUnusedDeductionRequest(person)

    verify(adjustmentsApiClient).updateEffectiveDays(AdjustmentEffectiveDays(taggedBail.id!!, 0, person))
    verify(adjustmentsApiClient).updateEffectiveDays(AdjustmentEffectiveDays(remand.id!!, 80, person))
    verify(adjustmentsApiClient).createAdjustment(
      remand.copy(
        id = null,
        toDate = null,
        days = 100,
        adjustmentType = AdjustmentServiceAdjustmentType.UNUSED_DEDUCTIONS,
      ),
    )
  }

  @Test
  fun updateUnusedDeductions_ZeroCalculatedDays() {
    val person = "ABC123"
    val remand = AdjustmentServiceAdjustment(
      UUID.randomUUID(), 1, 1, person, AdjustmentServiceAdjustmentType.REMAND, LocalDate.now().minusDays(100),
      LocalDate.now().minusDays(9), null, 90, 80,
    )
    val taggedBail = remand.copy(id = UUID.randomUUID(), adjustmentType = AdjustmentServiceAdjustmentType.TAGGED_BAIL, days = 90, daysBetween = null)
    val unusedDeductions = remand.copy(id = UUID.randomUUID(), adjustmentType = AdjustmentServiceAdjustmentType.UNUSED_DEDUCTIONS, days = 10, effectiveDays = 10, daysBetween = null)
    val adjustments = listOf(remand, taggedBail, unusedDeductions)

    whenever(adjustmentsApiClient.getAdjustmentsByPerson(person)).thenReturn(adjustments)
    whenever(unusedDeductionsCalculationService.calculate(adjustments, person)).thenReturn(0)

    unusedDeductionsService.handleUnusedDeductionRequest(person)

    verify(adjustmentsApiClient).updateEffectiveDays(AdjustmentEffectiveDays(taggedBail.id!!, 90, person))
    verify(adjustmentsApiClient).updateEffectiveDays(AdjustmentEffectiveDays(remand.id!!, 90, person))
    verify(adjustmentsApiClient).deleteAdjustment(unusedDeductions.id!!)
  }
}
