package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class InvalidNomisOffenceCodeValidator(
  @Value($$"${remand-and-sentencing.ui.url}") private val remandAndSentencingUiUrl: String,
) : PreCalculationSourceDataValidator {

  override fun validate(
    sourceData: CalculationSourceData,
  ): List<ValidationMessage> = sourceData.sentenceAndOffences.map { it }
    .filter { sentenceAndOffence -> isInactiveNomisOffenceCode(sentenceAndOffence.offence.offenceCode) }
    .map { sentenceAndOffence ->
      ValidationMessage(
        ValidationCode.INVALID_NOMIS_OFFENCE_CODE,
        listOf(
          sentenceAndOffence.offence.offenceCode,
          sentenceAndOffence.offence.offenceDescription,
          sentenceAndOffence.caseReference?.let { " from case $it" } ?: "",
          remandAndSentencingUiUrl,
          sourceData.prisonerDetails.offenderNo,
        ),
      )
    }

  private fun isInactiveNomisOffenceCode(offenceCode: String): Boolean = offenceCode.contains("-") || offenceCode.startsWith("XX") || offenceCode.endsWith("N")

  override fun validationOrder() = ValidationOrder.INVALID
}
