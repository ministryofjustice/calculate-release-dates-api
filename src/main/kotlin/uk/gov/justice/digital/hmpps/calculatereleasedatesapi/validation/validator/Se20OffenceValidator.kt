package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SENTENCING_ACT_2020_COMMENCEMENT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class Se20OffenceValidator : PreCalculationSourceDataValidator {
  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> {
    val invalidOffences = sourceData.sentenceAndOffences.filter {
      it.offence.offenceCode.startsWith("SE20") &&
        it.offence.offenceStartDate?.isBefore(SENTENCING_ACT_2020_COMMENCEMENT) ?: false
    }

    return if (invalidOffences.size == 1) {
      listOf(
        ValidationMessage(
          ValidationCode.SE2020_INVALID_OFFENCE_DETAIL,
          listOf(invalidOffences.first().offence.offenceCode),
        ),
      )
    } else {
      invalidOffences.map {
        ValidationMessage(
          ValidationCode.SE2020_INVALID_OFFENCE_COURT_DETAIL,
          listOf(it.caseSequence.toString(), it.lineSequence.toString()),
        )
      }
    }
  }

  override fun validationOrder() = ValidationOrder.INVALID
}
