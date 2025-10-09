package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class SupportedSentencesValidator : PreCalculationSourceDataValidator {
  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> {
    val supportedCategories = listOf("2003", "2020")
    val validationMessages = sourceData.sentenceAndOffences.filter {
      !SentenceCalculationType.isCalculable(it.sentenceCalculationType) ||
        !supportedCategories.contains(it.sentenceCategory)
    }.map {
      val displayName = SentenceCalculationType.displayName(it)
      ValidationMessage(ValidationCode.UNSUPPORTED_SENTENCE_TYPE, listOf(displayName))
    }.toMutableList()

    return validationMessages.toList()
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED
}
