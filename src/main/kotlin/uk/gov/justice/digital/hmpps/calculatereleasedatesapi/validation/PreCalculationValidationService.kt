package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BotusSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetentionAndTrainingOrderSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.PCSC_COMMENCEMENT_DATE

@Service
class PreCalculationValidationService(
  private val featureToggles: FeatureToggles,
  private val fineValidationService: FineValidationService,
  private val adjustmentValidationService: AdjustmentValidationService,
  private val dtoValidationService: DtoValidationService,
  private val botusValidationService: BotusValidationService,
  private val unsupportedValidationService: UnsupportedValidationService,
) {

  internal fun validatePrePcscDtoDoesNotHaveRemandOrTaggedBail(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    val adjustments = mutableSetOf<SentenceAdjustmentType>()
    sourceData.bookingAndSentenceAdjustments.sentenceAdjustments.forEach { adjustment ->
      if (adjustment.type == SentenceAdjustmentType.REMAND || adjustment.type == SentenceAdjustmentType.TAGGED_BAIL) {
        val sentence = sourceData.sentenceAndOffences.firstOrNull { it.sentenceSequence == adjustment.sentenceSequence }
        if (sentence != null && SentenceCalculationType.from(sentence.sentenceCalculationType).sentenceClazz == DetentionAndTrainingOrderSentence::class.java && sentence.sentenceDate.isBefore(
            PCSC_COMMENCEMENT_DATE,
          )
        ) {
          adjustments.add(adjustment.type)
        }
      }
    }
    if (adjustments.size > 0) {
      val adjustmentString = adjustments.joinToString(separator = " and ") { it.toString().lowercase() }
      messages.add(ValidationMessage(ValidationCode.PRE_PCSC_DTO_WITH_ADJUSTMENT, listOf(adjustmentString.replace("_", " "))))
    }
    return messages
  }

  fun validateOffenderSupported(prisonerDetails: PrisonerDetails): List<ValidationMessage> {
    val hasPtdAlert = prisonerDetails.activeAlerts().any {
      it.alertCode == "PTD" && it.alertType == "O"
    }

    if (hasPtdAlert) {
      return listOf(ValidationMessage(ValidationCode.PRISONER_SUBJECT_TO_PTD))
    }
    return emptyList()
  }
  fun validateSupportedSentences(sentencesAndOffences: List<SentenceAndOffence>): List<ValidationMessage> {
    val supportedCategories = listOf("2003", "2020")
    val validationMessages = sentencesAndOffences.filter {
      if (SentenceCalculationType.isSupported(it.sentenceCalculationType) && supportedCategories.contains(it.sentenceCategory)) {
        val type = from(it.sentenceCalculationType)
        !featureToggles.botus && type.sentenceClazz == BotusSentence::class.java
      } else {
        true
      }
    }
      .map {
        ValidationMessage(
          ValidationCode.UNSUPPORTED_SENTENCE_TYPE,
          listOf(it.sentenceCategory, it.sentenceTypeDescription),
        )
      }
      .toMutableList()
    return validationMessages.toList()
  }

  fun validateUnsupportedCalculation(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    val messages = fineValidationService.validateFineSentenceSupported(sourceData).toMutableList()
    messages += adjustmentValidationService.validateSupportedAdjustments(sourceData.bookingAndSentenceAdjustments.bookingAdjustments)
    messages += dtoValidationService.validate(sourceData)
    messages += botusValidationService.validate(sourceData)
    return messages
  }

  internal fun validateUnsupportedOffences(sentencesAndOffence: List<SentenceAndOffenceWithReleaseArrangements>): List<ValidationMessage> {
    val messages = unsupportedValidationService.validateUnsupportedEncouragingOffences(sentencesAndOffence).toMutableList()
    messages += unsupportedValidationService.validateUnsupported97BreachOffencesAfter1Dec2020(sentencesAndOffence)
    messages += unsupportedValidationService.validateUnsupportedSuspendedOffences(sentencesAndOffence)
    return messages
  }
}
