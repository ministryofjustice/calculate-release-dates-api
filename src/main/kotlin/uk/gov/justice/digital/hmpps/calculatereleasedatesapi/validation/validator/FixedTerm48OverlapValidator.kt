package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.FTR_48_COMMENCEMENT_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import java.time.temporal.ChronoUnit.MONTHS

@Component
class FixedTerm48OverlapValidator(private val featureToggles: FeatureToggles) : PostCalculationValidator {

  override fun validate(
    calculationOutput: CalculationOutput,
    booking: Booking,
  ): List<ValidationMessage> {
    if (!featureToggles.ftr48ManualJourney) return emptyList()

    val possibleSentences = calculationOutput.sentences.filter {
      val relativeDate = it.recall?.revocationDate ?: it.sentencedAt
      it.recallType == FIXED_TERM_RECALL_28 && relativeDate.isBefore(FTR_48_COMMENCEMENT_DATE)
    }
    val allSentencesLessThan12Months = possibleSentences.all { it.durationIsLessThan(12, MONTHS) }
    val anySentenceEqualOrOver48Months = possibleSentences.any { it.durationIsGreaterThanOrEqualTo(48, MONTHS) }
    if (possibleSentences.isNotEmpty() && !allSentencesLessThan12Months && !anySentenceEqualOrOver48Months) {
      return listOf(ValidationMessage(ValidationCode.FTR_TYPE_48_DAYS_OVERLAPPING_SENTENCE))
    }
    return emptyList()
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED
}
