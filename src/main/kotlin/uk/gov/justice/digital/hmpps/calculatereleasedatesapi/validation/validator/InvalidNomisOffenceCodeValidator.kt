package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class InvalidNomisOffenceCodeValidator : PreCalculationSourceDataValidator {

  override fun validate(
    sourceData: CalculationSourceData,
  ): List<ValidationMessage> = sourceData.sentenceAndOffences
    .filter { sentenceAndOffence -> isInactiveNomisOffenceCode(sentenceAndOffence.offence.offenceCode.uppercase()) }
    .map { sentenceAndOffence ->
      listOf(
        sentenceAndOffence.offence.offenceCode,
        sentenceAndOffence.offence.offenceDescription,
        sentenceAndOffence.caseReference?.let { " from case $it" } ?: "",
      )
    }.toSet()
    .map { uniqueArgs -> ValidationMessage(ValidationCode.INVALID_NOMIS_OFFENCE_CODE, uniqueArgs) }

  private fun isInactiveNomisOffenceCode(offenceCode: String): Boolean = offenceCode.contains("-") || offenceCode.startsWith("XX") || offenceCode.endsWith("N")

  override fun validationOrder() = ValidationOrder.INVALID
}
