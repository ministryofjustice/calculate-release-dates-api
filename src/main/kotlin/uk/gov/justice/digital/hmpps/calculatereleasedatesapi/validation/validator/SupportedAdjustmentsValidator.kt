package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationUtilities

@Component
class SupportedAdjustmentsValidator(private val validationUtilities: ValidationUtilities) : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> = sourceData.bookingAndSentenceAdjustments.fold(
    { validateIfAdjustmentsAreSupported(it) },
    { validateIfAdjustmentsAreSupported(it) },
  )

  private fun validateIfAdjustmentsAreSupported(adjustments: BookingAndSentenceAdjustments): List<ValidationMessage> = mutableListOf<ValidationMessage>().apply {
    addAll(lawfullyAtLargeIsNotSupported(adjustments.bookingAdjustments))
    addAll(specialRemissionIsNotSupported(adjustments.bookingAdjustments))
    addAll(timeSpentInCustodyAbroadIsNotSupported(adjustments.sentenceAdjustments))
    addAll(timeSpentAsAnAppealApplicantIsNotSupported(adjustments.sentenceAdjustments))
  }

  private fun validateIfAdjustmentsAreSupported(adjustments: List<AdjustmentDto>): List<ValidationMessage> = mutableListOf<ValidationMessage>().apply {
    adjustments.forEach {
      when (it.adjustmentType) {
        AdjustmentDto.AdjustmentType.LAWFULLY_AT_LARGE -> add(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE))
        AdjustmentDto.AdjustmentType.SPECIAL_REMISSION -> add(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION))
        AdjustmentDto.AdjustmentType.CUSTODY_ABROAD -> add(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_TIME_SPENT_IN_CUSTODY_ABROAD))
        AdjustmentDto.AdjustmentType.APPEAL_APPLICANT -> add(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_TIME_SPENT_AS_AN_APPEAL_APPLICANT))
        else -> return@forEach
      }
    }
  }

  private fun lawfullyAtLargeIsNotSupported(adjustments: List<BookingAdjustment>): List<ValidationMessage> = if (adjustments.any { it.type == BookingAdjustmentType.LAWFULLY_AT_LARGE }) {
    listOf(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE))
  } else {
    emptyList()
  }

  private fun specialRemissionIsNotSupported(adjustments: List<BookingAdjustment>): List<ValidationMessage> = if (adjustments.any { it.type == BookingAdjustmentType.SPECIAL_REMISSION }) {
    listOf(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION))
  } else {
    emptyList()
  }
  private fun timeSpentInCustodyAbroadIsNotSupported(adjustments: List<SentenceAdjustment>): List<ValidationMessage> = if (adjustments.any { it.type == SentenceAdjustmentType.TIME_SPENT_IN_CUSTODY_ABROAD }) {
    listOf(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_TIME_SPENT_IN_CUSTODY_ABROAD))
  } else {
    emptyList()
  }

  private fun timeSpentAsAnAppealApplicantIsNotSupported(adjustments: List<SentenceAdjustment>): List<ValidationMessage> = if (adjustments.any { it.type == SentenceAdjustmentType.TIME_SPENT_AS_AN_APPEAL_APPLICANT }) {
    listOf(ValidationMessage(ValidationCode.UNSUPPORTED_ADJUSTMENT_TIME_SPENT_AS_AN_APPEAL_APPLICANT))
  } else {
    emptyList()
  }

  private fun isFineSentence(sentencesAndOffence: SentenceAndOffence): Boolean = SentenceCalculationType.from(sentencesAndOffence.sentenceCalculationType).sentenceType == SentenceType.AFine
  override fun validationOrder() = ValidationOrder.UNSUPPORTED
}
