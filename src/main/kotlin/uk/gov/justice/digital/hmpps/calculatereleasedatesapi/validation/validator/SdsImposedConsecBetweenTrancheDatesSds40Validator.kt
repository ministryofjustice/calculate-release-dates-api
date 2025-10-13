package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class SdsImposedConsecBetweenTrancheDatesSds40Validator(private val trancheConfiguration: SDS40TrancheConfiguration) : PostCalculationValidator {

  override fun validate(
    calculationOutput: CalculationOutput,
    booking: Booking,
  ): List<ValidationMessage> {
    if (calculationOutput.calculationResult.sdsEarlyReleaseTranche == SDSEarlyReleaseTranche.TRANCHE_2 &&
      calculationOutput.sentences.filterIsInstance<ConsecutiveSentence>().any { consecutiveSentence ->
        consecutiveSentence.orderedSentences.any {
          it is StandardDeterminateSentence &&
            !it.isSDSPlus &&
            it.sentencedAt.isAfterOrEqualTo(trancheConfiguration.trancheOneCommencementDate) &&
            it.sentencedAt.isBefore(trancheConfiguration.trancheTwoCommencementDate)
        }
      }
    ) {
      log.info("Unsupported SDS sentence consecutive between tranche dates for booking ${booking.bookingId}")
      return listOf(ValidationMessage(ValidationCode.UNSUPPORTED_SDS40_CONSECUTIVE_SDS_BETWEEN_TRANCHE_COMMENCEMENTS))
    }
    return emptyList()
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
