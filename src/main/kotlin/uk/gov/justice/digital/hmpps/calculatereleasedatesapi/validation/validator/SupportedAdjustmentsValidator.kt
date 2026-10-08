package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class SupportedAdjustmentsValidator : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> = validateIfAdjustmentsAreSupported(sourceData.bookingAndSentenceAdjustments.adjustmentsApiData)

  private fun validateIfAdjustmentsAreSupported(adjustments: List<AdjustmentDto>): List<ValidationMessage> = mutableListOf<ValidationMessage>().apply {
    adjustments.forEach {
      when (it.adjustmentType) {
        AdjustmentDto.AdjustmentType.LAWFULLY_AT_LARGE -> add(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE))
        AdjustmentDto.AdjustmentType.SPECIAL_REMISSION -> add(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION))
        AdjustmentDto.AdjustmentType.CUSTODY_ABROAD -> add(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_TIME_SPENT_IN_CUSTODY_ABROAD))
        AdjustmentDto.AdjustmentType.APPEAL_APPLICANT -> add(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_TIME_SPENT_AS_AN_APPEAL_APPLICANT))
        else -> return@forEach
      }
    }
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED
}
