package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class OffenderSupportedValidator : PreCalculationSourceDataValidator {
  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> {
    val hasPtdAlert = sourceData.prisonerDetails.activeAlerts().any {
      it.alertCode == "PTD" && it.alertType == "O"
    }

    if (hasPtdAlert) {
      return listOf(ValidationMessage(ValidationCode.PRISONER_SUBJECT_TO_PTD))
    }
    return emptyList()
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED
}
