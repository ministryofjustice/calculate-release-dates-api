package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import java.time.LocalDate

@Component
class RevocationDateValidator : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> {
    val recallSentences = sourceData.sentenceAndOffences.any { from(it.sentenceCalculationType).recallType != null }

    if (recallSentences) {
      val revocationDate = sourceData.findLatestRevocationDate()
      if (revocationDate != null && revocationDate.isAfter(LocalDate.now())) {
        return listOf(ValidationMessage(ValidationCode.REVOCATION_DATE_IN_THE_FUTURE))
      }
    }
    return emptyList()
  }

  override fun validationOrder() = ValidationOrder.INVALID
}
