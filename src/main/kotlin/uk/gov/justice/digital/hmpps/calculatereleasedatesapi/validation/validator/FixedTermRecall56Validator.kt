package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementDirection
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_56
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import java.time.LocalDate

@Component
class FixedTermRecall56Validator : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> {
    if (!sourceData.sentenceAndOffences.mapNotNull { from(it.sentenceCalculationType).recallType }.contains(FIXED_TERM_RECALL_56)) {
      return emptyList()
    }

    val lastAdmission = sourceData.movements
      .filter { it.transformMovementDirection() == ExternalMovementDirection.IN }
      .maxByOrNull { it.movementDate ?: LocalDate.MIN }
      ?: return emptyList()

    val lastHdcOrECSLRelease = sourceData.movements
      .filter {
        it.transformMovementDirection() == ExternalMovementDirection.OUT &&
          it.movementDate != null &&
          it.movementDate < lastAdmission.movementDate
      }
      .maxByOrNull { it.movementDate ?: LocalDate.MIN }

    val reason = lastHdcOrECSLRelease?.transformMovementReason()
    if (reason == ExternalMovementReason.HDC || reason == ExternalMovementReason.ECSL) {
      return listOf(
        ValidationMessage(
          ValidationCode.FTR_TYPE_56_UNSUPPORTED_RECALL,
          listOf(reason.name),
        ),
      )
    }

    return emptyList()
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED
}
