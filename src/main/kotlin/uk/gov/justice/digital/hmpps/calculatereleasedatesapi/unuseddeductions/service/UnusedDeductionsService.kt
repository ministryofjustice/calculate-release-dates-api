package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.AdjustmentEffectiveDays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.AdjustmentServiceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.AdjustmentServiceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.AdjustmentsApiClient
import kotlin.math.max

@Service
class UnusedDeductionsService(
  val adjustmentsApiClient: AdjustmentsApiClient,
  val unusedDeductionsCalculationService: UnusedDeductionsCalculationService,
) {

  fun handleUnusedDeductionRequest(person: String) {
    log.info("Received message for adjustment change")
    val adjustments = adjustmentsApiClient.getAdjustmentsByPerson(person)
    val deductions = adjustments
      .filter { it.adjustmentType === AdjustmentServiceAdjustmentType.REMAND || it.adjustmentType === AdjustmentServiceAdjustmentType.TAGGED_BAIL }

    if (deductions.isEmpty()) {
      setUnusedDeductions(0, adjustments, deductions)
      return
    }

    val allDeductionsEnteredInDps = deductions.all { it.days != null || it.daysBetween != null }

    if (allDeductionsEnteredInDps) {
      val unusedDeductions =
        unusedDeductionsCalculationService.calculate(adjustments, person)

      if (unusedDeductions == null) {
        // Couldn't calculate.
        return
      }

      setUnusedDeductions(unusedDeductions, adjustments, deductions)
      setEffectiveDays(unusedDeductions, deductions)
    }
  }

  private fun setEffectiveDays(unusedDeductions: Int, deductions: List<AdjustmentServiceAdjustment>) {
    var remainingDeductions = unusedDeductions
    // Tagged bail first.
    deductions.sortedByDescending { it.adjustmentType.name }.forEach {
      val days = if (it.adjustmentType == AdjustmentServiceAdjustmentType.TAGGED_BAIL) {
        it.days!!
      } else {
        it.daysBetween!!
      }
      val effectiveDays = max(days - remainingDeductions, 0)
      remainingDeductions -= days
      remainingDeductions = max(remainingDeductions, 0)
      if (effectiveDays != it.effectiveDays) {
        adjustmentsApiClient.updateEffectiveDays(AdjustmentEffectiveDays(it.id!!, effectiveDays, it.person))
      }
    }
  }

  private fun setUnusedDeductions(
    unusedDeductions: Int,
    adjustments: List<AdjustmentServiceAdjustment>,
    deductions: List<AdjustmentServiceAdjustment>,
  ) {
    val unusedDeductionsAdjustment =
      adjustments.find { it.adjustmentType == AdjustmentServiceAdjustmentType.UNUSED_DEDUCTIONS }
    if (unusedDeductionsAdjustment != null) {
      if (unusedDeductions == 0) {
        adjustmentsApiClient.deleteAdjustment(unusedDeductionsAdjustment.id!!)
      } else {
        if (unusedDeductionsAdjustment.days != unusedDeductions) {
          adjustmentsApiClient.updateAdjustment(unusedDeductionsAdjustment.copy(days = unusedDeductions))
        }
      }
    } else {
      if (unusedDeductions > 0) {
        val aDeduction = deductions[0]
        adjustmentsApiClient.createAdjustment(
          aDeduction.copy(
            id = null,
            toDate = null,
            days = unusedDeductions,
            adjustmentType = AdjustmentServiceAdjustmentType.UNUSED_DEDUCTIONS,
          ),
        )
      }
    }
  }

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
