package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import java.time.LocalDate
import kotlin.collections.forEach
import kotlin.collections.plusAssign

@Component
class UnsupportedOffenceValidator : PreCalculationSourceDataValidator {

  override fun validate(
    sourceData: CalculationSourceData,
  ): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()

    unsupportedOffenceCodeValidationMap.forEach { (predicate, code) ->
      if (sourceData.sentenceAndOffences.any(predicate)) {
        messages += ValidationMessage(code)
      }
    }

    return messages
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED

  companion object {
    private val AFTER_97_BREACH_PROVISION_INVALID = LocalDate.of(2020, 12, 1)

    private val unsupportedOffenceCodeValidationMap: Map<(SentenceAndOffenceWithReleaseArrangements) -> Boolean, ValidationCode> =
      mapOf(
        { it: SentenceAndOffenceWithReleaseArrangements -> it.offence.offenceCode == "CL77036" } to
          ValidationCode.UNSUPPORTED_GENERIC_CONSPIRACY_OFFENCE,

        { it: SentenceAndOffenceWithReleaseArrangements ->
          it.offence.offenceCode in (2..13).map { i -> "SC070${"%02d".format(i)}" }
        } to ValidationCode.UNSUPPORTED_OFFENCE_ENCOURAGING_OR_ASSISTING,

        { it: SentenceAndOffenceWithReleaseArrangements ->
          it.offence.offenceCode.startsWith("PH97003") &&
            it.offence.offenceStartDate?.isAfterOrEqualTo(AFTER_97_BREACH_PROVISION_INVALID) == true
        } to ValidationCode.UNSUPPORTED_BREACH_97,

        { it: SentenceAndOffenceWithReleaseArrangements ->
          it.offence.offenceCode in listOf("SE20512", "CJ03523")
        } to ValidationCode.UNSUPPORTED_SUSPENDED_OFFENCE,
      )
  }
}
