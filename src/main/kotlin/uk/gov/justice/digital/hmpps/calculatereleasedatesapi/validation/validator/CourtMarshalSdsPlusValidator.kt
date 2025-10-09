package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class CourtMarshalSdsPlusValidator : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> = if (sourceData.sentenceAndOffences.any { it.isSDSPlus && courtMarshalCourtTypeCodes.contains(it.courtTypeCode) }) {
    listOf(ValidationMessage(ValidationCode.COURT_MARTIAL_WITH_SDS_PLUS))
  } else {
    emptyList()
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED

  companion object {
    private val courtMarshalCourtTypeCodes = listOf("DCM", "GCM")
  }
}
