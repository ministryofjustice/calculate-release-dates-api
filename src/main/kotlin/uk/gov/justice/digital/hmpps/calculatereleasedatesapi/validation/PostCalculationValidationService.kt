package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo

@Service
class PostCalculationValidationService(
  private val trancheConfiguration: SDS40TrancheConfiguration,
  private val featureToggles: FeatureToggles,
) {

  internal fun validateSDSImposedConsecBetweenTrancheDatesForTrancheTwoPrisoner(
    booking: Booking,
    calculationResult: CalculationResult?,
  ): List<ValidationMessage> {
    if (featureToggles.sds40ConsecutiveManualJourney && calculationResult != null) {
      if (calculationResult.sdsEarlyReleaseTranche == SDSEarlyReleaseTranche.TRANCHE_2 &&
        booking.consecutiveSentences.any { consecutiveSentence ->
          consecutiveSentence.orderedSentences.any {
            it is StandardDeterminateSentence &&
              !it.isSDSPlus &&
              it.sentencedAt.isAfterOrEqualTo(trancheConfiguration.trancheOneCommencementDate) &&
              it.sentencedAt.isBefore(trancheConfiguration.trancheTwoCommencementDate)
          }
        }
      ) {
        return listOf(ValidationMessage(ValidationCode.UNSUPPORTED_SDS40_CONSECUTIVE_SDS_BETWEEN_TRANCHE_COMMENCEMENTS))
      }
    }
    return emptyList()
  }
}
