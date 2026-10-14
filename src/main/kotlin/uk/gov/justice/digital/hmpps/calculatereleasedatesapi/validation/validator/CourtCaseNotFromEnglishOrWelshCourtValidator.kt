package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class CourtCaseNotFromEnglishOrWelshCourtValidator : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> = if (
    sourceData.sentenceAndOffences.none { SentenceCalculationType.isIndeterminate(it.sentenceCalculationType) } &&
    sourceData.sentenceAndOffences.any { it.courtId in nonEnglishOrWelshCourtAgencyIds }
  ) {
    listOf(ValidationMessage(ValidationCode.CASE_NOT_FROM_ENGLISH_OR_WELSH_COURT))
  } else {
    emptyList()
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED

  companion object {
    // UK Courts that are not English or Welsh. Repatriated prisoners are handled in RepatriatedPrisonerValidator
    private val nonEnglishOrWelshCourtAgencyIds = setOf(
      "SCOTCT",
      "NICT",
      "GUERNS",
      "IOM",
      "IOMCTS", // only exists in dev
      "JERSEY",
    )
  }
}
