package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.PCSC_COMMENCEMENT_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SENTENCING_ACT_2020_COMMENCEMENT

@Service
class PreCalculationValidationService(
  private val fineValidationService: FineValidationService,
  private val adjustmentValidationService: AdjustmentValidationService,
  private val dtoValidationService: DtoValidationService,
  private val botusValidationService: BotusValidationService,
  private val unsupportedValidationService: UnsupportedValidationService,
  private val toreraValidationService: ToreraValidationService,
) {

  val courtMarshalCourtTypeCodes = listOf("DCM", "GCM")

  internal fun validatePrePcscDtoDoesNotHaveRemandOrTaggedBail(sourceData: CalculationSourceData): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    val adjustments = mutableSetOf<String>()
    val remandAndTaggedBail = getRemandAndTaggedBail(sourceData)

    remandAndTaggedBail.forEach { adjustment ->
      val sentence = sourceData.sentenceAndOffences.firstOrNull { it.sentenceSequence == adjustment.sentenceSequence }
      if (sentence != null &&
        SentenceCalculationType.from(sentence.sentenceCalculationType).sentenceType == SentenceType.DetentionAndTrainingOrder &&
        sentence.sentenceDate.isBefore(
          PCSC_COMMENCEMENT_DATE,
        )
      ) {
        adjustments.add(adjustment.type)
      }
    }

    if (adjustments.size > 0) {
      val adjustmentString = adjustments.joinToString(separator = " and ") { it.lowercase() }
      messages.add(
        ValidationMessage(
          ValidationCode.PRE_PCSC_DTO_WITH_ADJUSTMENT,
          listOf(adjustmentString.replace("_", " ")),
        ),
      )
    }
    return messages
  }

  private fun getRemandAndTaggedBail(sourceData: CalculationSourceData): List<RemandAndTaggedBail> = sourceData.bookingAndSentenceAdjustments.fold(
    { adjustments -> adjustments.sentenceAdjustments.filter { it.type == SentenceAdjustmentType.REMAND || it.type == SentenceAdjustmentType.TAGGED_BAIL }.map { RemandAndTaggedBail(it.sentenceSequence, it.type.toString()) } },
    { adjustments -> adjustments.filter { it.adjustmentType == AdjustmentDto.AdjustmentType.REMAND || it.adjustmentType == AdjustmentDto.AdjustmentType.TAGGED_BAIL }.map { RemandAndTaggedBail(it.sentenceSequence!!, it.adjustmentType.toString()) } },
  )

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
      !SentenceCalculationType.isCalculable(it.sentenceCalculationType) ||
        !supportedCategories.contains(it.sentenceCategory)
    }.map {
      val displayName = SentenceCalculationType.displayName(it)
      ValidationMessage(ValidationCode.UNSUPPORTED_SENTENCE_TYPE, listOf(displayName))
    }.toMutableList()

    return validationMessages.toList()
  }

  fun validateUnsupportedCalculation(sourceData: CalculationSourceData): List<ValidationMessage> {
    val messages = fineValidationService.validateFineSentenceSupported(sourceData).toMutableList()
    messages += adjustmentValidationService.validateIfAdjustmentsAreSupported(sourceData.bookingAndSentenceAdjustments)
    messages += dtoValidationService.validate(sourceData)
    messages += botusValidationService.validate(sourceData)
    messages += toreraValidationService.validateToreraExempt(sourceData)
    return messages
  }

  internal fun validateUnsupportedOffences(sentencesAndOffence: List<SentenceAndOffenceWithReleaseArrangements>): List<ValidationMessage> = unsupportedValidationService.validateUnsupportedOffenceCodes(sentencesAndOffence)

  fun validateSe20Offences(data: CalculationSourceData): List<ValidationMessage> {
    val invalidOffences = data.sentenceAndOffences.filter {
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

  fun hasSentences(sentencesAndOffence: List<SentenceAndOffenceWithReleaseArrangements>): List<ValidationMessage> {
    if (sentencesAndOffence.isEmpty()) {
      return listOf(ValidationMessage(ValidationCode.NO_SENTENCES))
    }
    return emptyList()
  }

  fun isCourtMarshalWithSDSPlus(sourceData: CalculationSourceData): List<ValidationMessage> = if (sourceData.sentenceAndOffences.any { it.isSDSPlus && courtMarshalCourtTypeCodes.contains(it.courtTypeCode) }) {
    listOf(ValidationMessage(ValidationCode.COURT_MARTIAL_WITH_SDS_PLUS))
  } else {
    emptyList()
  }

  data class RemandAndTaggedBail(
    val sentenceSequence: Int,
    val type: String,
  )
}
