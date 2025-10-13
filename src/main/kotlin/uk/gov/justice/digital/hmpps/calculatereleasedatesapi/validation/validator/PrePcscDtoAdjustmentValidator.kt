package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.PCSC_COMMENCEMENT_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class PrePcscDtoAdjustmentValidator : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> {
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

  override fun validationOrder() = ValidationOrder.INVALID
}
data class RemandAndTaggedBail(
  val sentenceSequence: Int,
  val type: String,
)
