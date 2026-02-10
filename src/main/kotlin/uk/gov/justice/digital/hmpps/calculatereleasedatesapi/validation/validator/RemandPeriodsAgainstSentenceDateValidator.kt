package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationUtilities
import java.time.LocalDate

@Component
class RemandPeriodsAgainstSentenceDateValidator(private val validationUtilities: ValidationUtilities) : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> {
    val remandPeriods = sourceData.bookingAndSentenceAdjustments.fold(
      { adjustments ->
        adjustments.sentenceAdjustments
          .filter {
            it.type in setOf(
              SentenceAdjustmentType.REMAND,
              SentenceAdjustmentType.RECALL_SENTENCE_REMAND,
            ) &&
              it.fromDate != null &&
              it.toDate != null
          }.map {
            RemandPeriodToValidate(
              it.sentenceSequence,
              it.bookingId,
              it.fromDate!!,
              it.toDate!!,
            )
          }
      },
      { adjustments ->
        adjustments
          .filter {
            it.adjustmentType == AdjustmentDto.AdjustmentType.REMAND && it.fromDate != null && it.toDate != null
          }.map {
            RemandPeriodToValidate(
              it.sentenceSequence!!,
              it.bookingId,
              it.fromDate!!,
              it.toDate!!,
            )
          }
      },
    )

    val validationMessages = mutableListOf<ValidationMessage>()

    val sentenceMap = sourceData.sentenceAndOffences.associateBy { "${it.sentenceSequence}${it.bookingId}" }

    remandPeriods.forEach { remandPeriod ->
      val sentence = sentenceMap["${remandPeriod.sentenceSequence}${remandPeriod.bookingId}"]
      if (sentence != null) {
        val sentenceDate = sentence.sentenceDate

        val areRemandDatesAfterSentenceDate = remandPeriod.fromDate.isAfterOrEqualTo(sentenceDate) ||
          remandPeriod.toDate.isAfterOrEqualTo(sentenceDate)

        if (areRemandDatesAfterSentenceDate) {
          validationMessages.add(
            ValidationMessage(
              ValidationCode.REMAND_ON_OR_AFTER_SENTENCE_DATE,
              validationUtilities.getCaseSeqAndLineSeq(sentence),
            ),
          )
        }
      }
    }

    return validationMessages
  }

  override fun validationOrder() = ValidationOrder.INVALID
}
data class RemandPeriodToValidate(
  val sentenceSequence: Int,
  val bookingId: Long?,
  val fromDate: LocalDate,
  val toDate: LocalDate,
)
