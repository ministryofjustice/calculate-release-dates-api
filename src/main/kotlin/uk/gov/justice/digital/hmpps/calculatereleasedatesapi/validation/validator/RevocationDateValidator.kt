package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_56
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class RevocationDateValidator : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> {
    val recallSentences = sourceData.sentenceAndOffences.filter { from(it.sentenceCalculationType).recallType == FIXED_TERM_RECALL_56 }
    if (recallSentences.isNotEmpty() && recallSentences.none { it.revocationDates.isNotEmpty() }) {
      return listOf(ValidationMessage(ValidationCode.RECALL_MISSING_REVOCATION_DATE))
    }
    return emptyList()
  }

  override fun validationOrder() = ValidationOrder.INVALID
}
