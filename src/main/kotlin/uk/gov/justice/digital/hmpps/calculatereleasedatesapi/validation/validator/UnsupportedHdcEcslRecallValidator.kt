package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementDirection
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class UnsupportedHdcEcslRecallValidator : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> = validateUnsupportedConsecutiveBotusSentences(sourceData)

  private fun validateUnsupportedConsecutiveBotusSentences(sourceData: CalculationSourceData): List<ValidationMessage> {
    val hasFixedTermRecall =
      sourceData.sentenceAndOffences.any { from(it.sentenceCalculationType).recallType?.isFixedTermRecall == true }
    val lastRelease = sourceData.movements.filter { it.transformMovementDirection() == ExternalMovementDirection.OUT }
      .filter { it.movementDate == null }.maxByOrNull { it.movementDate!! }?.transformMovementReason()
    if (hasFixedTermRecall &&
      lastRelease != null &&
      listOf(
        ExternalMovementReason.ECSL,
        ExternalMovementReason.HDC,
      ).contains(lastRelease)
    ) {
      return listOf(ValidationMessage(ValidationCode.FTR_FROM_HDC_OR_ECSL))
    }
    return emptyList()
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED
}
