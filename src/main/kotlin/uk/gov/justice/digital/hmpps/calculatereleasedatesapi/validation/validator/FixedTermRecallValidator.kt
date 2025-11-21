package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementDirection
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_56
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import java.time.LocalDate

@Component
class FixedTermRecallValidator : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> {
    val ftrDetails = sourceData.fixedTermRecallDetails ?: return emptyList()
    val ftrValidationDetails = getFtrValidationDetails(
      ftrDetails,
      sourceData.sentenceAndOffences,
    )
    val (recallLength, has14DayFTRSentence, has28DayFTRSentence, has56DayFTRSentence) = ftrValidationDetails

    val validationMessages = when {
      sourceData.returnToCustodyDate == null -> mutableListOf(ValidationMessage(ValidationCode.FTR_NO_RETURN_TO_CUSTODY_DATE))
      has14DayFTRSentence && has28DayFTRSentence -> mutableListOf(ValidationMessage(ValidationCode.FTR_SENTENCES_CONFLICT_WITH_EACH_OTHER))
      has14DayFTRSentence && recallLength == 28 -> mutableListOf(ValidationMessage(ValidationCode.FTR_TYPE_14_DAYS_BUT_LENGTH_IS_28))
      has28DayFTRSentence && recallLength == 14 -> mutableListOf(ValidationMessage(ValidationCode.FTR_TYPE_28_DAYS_BUT_LENGTH_IS_14))
      else -> mutableListOf()
    }

    val fixedTermRecallSentences =
      sourceData.sentenceAndOffences.filter { from(it.sentenceCalculationType).recallType?.isFixedTermRecall == true }
    if (fixedTermRecallSentences.any { it.sentenceDate.isAfter(sourceData.returnToCustodyDate!!.returnToCustodyDate) }) {
      validationMessages.add(ValidationMessage(ValidationCode.FTR_RTC_DATE_BEFORE_SENTENCE_DATE))
    }

    if (fixedTermRecallSentences.isNotEmpty() &&
      sourceData.returnToCustodyDate!!.returnToCustodyDate.isAfter(
        LocalDate.now(),
      )
    ) {
      validationMessages.add(ValidationMessage(ValidationCode.FTR_RTC_DATE_IN_FUTURE))
    }

    val revocationDate = sourceData.findLatestRevocationDate()
    if (has56DayFTRSentence && revocationDate!!.isAfter(sourceData.returnToCustodyDate!!.returnToCustodyDate)) {
      validationMessages.add(ValidationMessage(ValidationCode.FTR_RTC_DATE_BEFORE_REVOCATION_DATE))
    }

    if (has56DayFTRSentence) {
      validateFtr56(sourceData)?.let { validationMessages.add(it) }
    }

    return validationMessages
  }

  private fun validateFtr56(sourceData: CalculationSourceData): ValidationMessage? {
    val lastAdmission = sourceData.movements
      .filter { it.transformMovementDirection() == ExternalMovementDirection.IN }
      .maxByOrNull { it.movementDate ?: LocalDate.MIN }
      ?: return null

    val lastHdcOrECSLRelease = sourceData.movements
      .filter {
        it.transformMovementDirection() == ExternalMovementDirection.OUT &&
          it.movementDate != null &&
          it.movementDate < lastAdmission.movementDate
      }
      .maxByOrNull { it.movementDate ?: LocalDate.MIN }

    val reason = lastHdcOrECSLRelease?.transformMovementReason()
    if (reason == ExternalMovementReason.HDC || reason == ExternalMovementReason.ECSL) {
      return ValidationMessage(
        ValidationCode.FTR_TYPE_56_UNSUPPORTED_RECALL,
        listOf(reason.name),
      )
    }

    return null
  }

  internal fun getFtrValidationDetails(
    ftrDetails: FixedTermRecallDetails,
    sentencesAndOffences: List<SentenceAndOffence>,
  ): FtrValidationDetails {
    val recallLength = ftrDetails.recallLength
    val bookingsSentenceTypes = sentencesAndOffences.mapNotNull { from(it.sentenceCalculationType).recallType }
    val has14DayFTRSentence = bookingsSentenceTypes.contains(FIXED_TERM_RECALL_14)
    val has28DayFTRSentence = bookingsSentenceTypes.contains(FIXED_TERM_RECALL_28)
    val has56DayFTRSentence = bookingsSentenceTypes.contains(FIXED_TERM_RECALL_56)
    return FtrValidationDetails(recallLength, has14DayFTRSentence, has28DayFTRSentence, has56DayFTRSentence)
  }

  override fun validationOrder() = ValidationOrder.INVALID
}

data class FtrValidationDetails(
  val recallLength: Int,
  val has14DayFTRSentence: Boolean,
  val has28DayFTRSentence: Boolean,
  val has56DayFTRSentence: Boolean,
)
