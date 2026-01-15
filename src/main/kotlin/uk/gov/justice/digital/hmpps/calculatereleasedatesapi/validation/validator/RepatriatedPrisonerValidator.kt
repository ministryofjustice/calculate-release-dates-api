package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class RepatriatedPrisonerValidator : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> = if (sourceData.sentenceAndOffences.any { it.courtId in repatriatedToCountryCourtAgencyIds }) {
    listOf(ValidationMessage(ValidationCode.REPATRIATED_PRISONER))
  } else {
    emptyList()
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED

  companion object {
    private val repatriatedToCountryCourtAgencyIds = setOf(
      "FORGN", // all envs
      "FORIGN", // dev only
    )
  }
}
