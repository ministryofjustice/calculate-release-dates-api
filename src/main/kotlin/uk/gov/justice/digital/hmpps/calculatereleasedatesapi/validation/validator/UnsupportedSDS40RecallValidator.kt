package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class UnsupportedSDS40RecallValidator(
  private val featureToggles: FeatureToggles,
  private val trancheConfiguration: SDS40TrancheConfiguration,
) : PostCalculationValidator {

  override fun validate(
    calculationOutput: CalculationOutput,
    booking: Booking,
  ): List<ValidationMessage> = if (hasUnsupportedRecallType(calculationOutput, booking)) {
    listOf(ValidationMessage(ValidationCode.UNSUPPORTED_SDS40_RECALL_SENTENCE_TYPE))
  } else {
    emptyList()
  }

  private fun hasUnsupportedRecallType(calculationOutput: CalculationOutput, booking: Booking): Boolean {
    if (!featureToggles.externalMovementsSds40) {
      return calculationOutput.sentences.any { sentence ->
        val hasTusedReleaseDateType = sentence.releaseDateTypes.contains(ReleaseDateType.TUSED)
        val isDeterminateOrConsecutiveSentence = sentence.sentenceParts().any { it is StandardDeterminateSentence }
        val sentencedBeforeCommencement = sentence.sentencedAt.isBefore(trancheConfiguration.trancheOneCommencementDate)
        val adjustedReleaseDateAfterOrEqualCommencement =
          sentence.sentenceCalculation.adjustedHistoricDeterminateReleaseDate
            .isAfterOrEqualTo(trancheConfiguration.trancheOneCommencementDate)

        val result = hasTusedReleaseDateType &&
          isDeterminateOrConsecutiveSentence &&
          sentence.isRecall() &&
          sentencedBeforeCommencement &&
          adjustedReleaseDateAfterOrEqualCommencement

        if (result) {
          log.info(
            "Unsupported recall type found for sentence ${
              sentence.sentenceParts().map { (it as AbstractSentence).identifier }
            } in booking for ${booking.offender.reference}.",
          )
        }
        return@any result
      }
    }
    // SDS40 recalls are supported with external movements.
    return false
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED

  companion object {
    private const val TWELVE = 12L
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
