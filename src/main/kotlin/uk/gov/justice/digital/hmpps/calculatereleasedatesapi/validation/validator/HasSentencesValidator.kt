package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class HasSentencesValidator : PreCalculationSourceDataValidator {
  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> {
    if (sourceData.sentenceAndOffences.isEmpty()) {
      return listOf(ValidationMessage(ValidationCode.NO_SENTENCES))
    }
    return emptyList()
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED
}
