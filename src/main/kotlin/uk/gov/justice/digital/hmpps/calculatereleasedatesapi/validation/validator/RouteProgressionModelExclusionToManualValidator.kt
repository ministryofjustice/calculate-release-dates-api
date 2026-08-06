package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class RouteProgressionModelExclusionToManualValidator(private val featureToggles: FeatureToggles) : PreCalculationBookingValidator {

  override fun validate(booking: Booking): List<ValidationMessage> = if (
    featureToggles.routeProgressionModelScheduleExclusionToManual && hasASentenceWithTheExclusion(booking.sentences)
  ) {
    listOf(ValidationMessage(ValidationCode.PROGRESSION_MODEL_SCHEDULE_EXCLUSION))
  } else {
    emptyList()
  }

  private fun hasASentenceWithTheExclusion(sentenceAndOffences: List<AbstractSentence>): Boolean = sentenceAndOffences.any { sentence ->
    sentence is StandardDeterminateSentence &&
      // section 250 is excluded from PM anyway
      !sentence.releaseArrangements.isSection250 &&
      // recalls are also not subject to progression model for calculating the PRRD
      !sentence.isRecall() &&
      SDSEarlyReleaseExclusionType.SA2026_PROGRESSION_MODEL_SCHEDULE in sentence.releaseArrangements.sdsEarlyReleaseExclusions
  }

  override fun validationOrder(): ValidationOrder = ValidationOrder.UNSUPPORTED
}
