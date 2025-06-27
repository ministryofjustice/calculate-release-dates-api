package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SDS_DYO_TORERA_START_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SOPC_TORERA_END_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ManageOffencesService

@Service
class ToreraValidationService(
  private val manageOffencesService: ManageOffencesService,
) {

  internal fun validateToreraExempt(sourceData: CalculationSourceData): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    val sdsCodes = getToreraSdsOffenceCodes(sourceData)
    val sopcCodes = getToreraSopcOffenceCodes(sourceData)
    val sdsAndSopcOffenceCodes = sdsCodes.plus(sopcCodes)

    // if no SDS or SOPC offence codes are present, torera is not valid
    if (sdsAndSopcOffenceCodes.isEmpty()) {
      return messages
    }

    val scheduleOffenceCodes = manageOffencesService.getToreraOffenceCodes()

    // if any offence SDS offence is part of schedule 19ZA, trigger error
    if (sdsCodes.isNotEmpty() && scheduleOffenceCodes.any { it in sdsCodes }) {
      messages += listOf(ValidationMessage(ValidationCode.SDS_TORERA_EXCLUSION))
    }

    // if any offence SOPC offence is part of schedule 19ZA, trigger error
    if (sopcCodes.isNotEmpty() && scheduleOffenceCodes.any { it in sopcCodes }) {
      messages += listOf(ValidationMessage(ValidationCode.SOPC_TORERA_EXCLUSION))
    }

    return messages
  }

  /**
   * Any SDS sentences with a sentence date greater than 2005-04-04
   */
  internal fun getToreraSdsOffenceCodes(sourceData: CalculationSourceData): Set<String> = sourceData.sentenceAndOffences.filter { s ->
    SentenceCalculationType.isToreraEligible(
      s.sentenceCalculationType,
      eligibilityType = SentenceCalculationType.ToreraEligibilityType.SDS,
    ) &&
      s.sentenceDate > SDS_DYO_TORERA_START_DATE
  }
    .map { it.offence.offenceCode }
    .toSet()

  /**
   * Any SOPC sentences with a sentence date before 2022-06-28
   */
  internal fun getToreraSopcOffenceCodes(sourceData: CalculationSourceData): Set<String> = sourceData.sentenceAndOffences.filter { s ->
    SentenceCalculationType.isToreraEligible(
      s.sentenceCalculationType,
      eligibilityType = SentenceCalculationType.ToreraEligibilityType.SOPC,
    ) &&
      s.sentenceDate < SOPC_TORERA_END_DATE
  }
    .map { it.offence.offenceCode }
    .toSet()
}
