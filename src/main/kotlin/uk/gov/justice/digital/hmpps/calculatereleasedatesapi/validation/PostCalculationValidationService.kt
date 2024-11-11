package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate

@Service
class PostCalculationValidationService(
  private val trancheConfiguration: SDS40TrancheConfiguration,
  private val featureToggles: FeatureToggles,
) {

  private val shpoSX03OffenceCodes = listOf("SX03220", "SX03220A", "SX03244", "SX03244A", "SX03247", "SX03247A")
  private val shpoBreachOffenceFromDate = LocalDate.of(2020, 12, 1)

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
        log.info("Unsupported SDS sentence consecutive between tranche dates for booking ${booking.bookingId}")
        return listOf(ValidationMessage(ValidationCode.UNSUPPORTED_SDS40_CONSECUTIVE_SDS_BETWEEN_TRANCHE_COMMENCEMENTS))
      }
    }
    return emptyList()
  }

  internal fun validateSHPOContainingSX03Offences(
    booking: Booking,
    calculationResult: CalculationResult?,
  ): List<ValidationMessage> {
    if (calculationResult != null) {
      val matchingSentences = booking.getAllExtractableSentences()
        .filter {
          it.sentenceCalculation.adjustedDeterminateReleaseDate.isAfterOrEqualTo(trancheConfiguration.trancheOneCommencementDate)
        }
        .flatMap { if (it is ConsecutiveSentence) it.orderedSentences else listOf(it) }
        .filter {
          it is StandardDeterminateSentence &&
            !it.isSDSPlus &&
            shpoSX03OffenceCodes.contains(it.offence.offenceCode) &&
            it.offence.committedAt.isAfterOrEqualTo(shpoBreachOffenceFromDate)
        }

      // The result having a tranche other than 0 would mean it falls under the SDS40 criteria for assessment,
      // However the tranche may be 0 for sentences after tranche 1.
      if (matchingSentences.any() && (
          calculationResult.sdsEarlyReleaseTranche != SDSEarlyReleaseTranche.TRANCHE_0 ||
            matchingSentences.any { it.sentencedAt.isAfterOrEqualTo(trancheConfiguration.trancheOneCommencementDate) }
          )
      ) {
        log.info("Unable to determine release provision for SHPO SX03 offence for ${booking.bookingId}")
        return listOf(ValidationMessage(ValidationCode.UNABLE_TO_DETERMINE_SHPO_RELEASE_PROVISIONS))
      }
    }
    return emptyList()
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
