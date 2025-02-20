package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
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
    calculationOutput: CalculationOutput,
  ): List<ValidationMessage> {
    if (featureToggles.sds40ConsecutiveManualJourney) {
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
    }
    return emptyList()
  }

  internal fun validateSHPOContainingSX03Offences(
    booking: Booking,
    calculationOutput: CalculationOutput,
  ): List<ValidationMessage> = calculationOutput.sentences
    .filter(::filterSXOffences)
    .filter(::filterSXOffenceDates)
    .ifEmpty { return emptyList() }
    .let { shpoList ->
      if (
        calculationOutput.calculationResult.sdsEarlyReleaseTranche != SDSEarlyReleaseTranche.TRANCHE_0 ||
        shpoList.any { it.sentencedAt.isAfterOrEqualTo(trancheConfiguration.trancheOneCommencementDate) }
      ) {
        log.info("Unable to determine release provision for SHPO SX03 offence for ${booking.bookingId}")
        listOf(ValidationMessage(ValidationCode.UNABLE_TO_DETERMINE_SHPO_RELEASE_PROVISIONS))
      } else {
        emptyList()
      }
    }

  /**
   * Only return SX03 offences where the offence is NOT SDS-Plus
   * AND the offence is committed between the SHPO breach offence commencement date and the Tranche 3 commencement date
   *
   * This is a period of time when SDS-40 may or may not apply
   */
  private fun filterSXOffences(sentence: CalculableSentence): Boolean = sentence.sentenceParts().any {
    it is StandardDeterminateSentence &&
      !it.isSDSPlus &&
      shpoSX03OffenceCodes.contains(it.offence.offenceCode) &&
      (it.offence.committedAt >= shpoBreachOffenceFromDate && it.offence.committedAt <= trancheConfiguration.trancheThreeCommencementDate)
  }

  /**
   * Only return where sentence is post SDS-40 commencement date AND
   * is either a Recall with a top-up supervision date OR the sentence is not a Recall
   * AND the release date is before the Tranche 3 commencement date
   *
   * All releases post Tranche 3 commencement date are SDS-50
   */
  private fun filterSXOffenceDates(sentence: CalculableSentence): Boolean =
    sentence.sentenceCalculation.adjustedDeterminateReleaseDate.isAfterOrEqualTo(trancheConfiguration.trancheOneCommencementDate) &&
      sentence.sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(trancheConfiguration.trancheThreeCommencementDate) &&
      (!sentence.isRecall() || sentence.sentenceCalculation.topUpSupervisionDate != null)

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
