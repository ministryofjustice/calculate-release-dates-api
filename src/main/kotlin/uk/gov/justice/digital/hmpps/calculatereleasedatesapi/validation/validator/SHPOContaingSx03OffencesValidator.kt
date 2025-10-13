package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SHPO_BREACH_OFFENCE_FROM_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class SHPOContaingSx03OffencesValidator(private val trancheConfiguration: SDS40TrancheConfiguration) : PostCalculationValidator {

  override fun validate(
    calculationOutput: CalculationOutput,
    booking: Booking,
  ): List<ValidationMessage> {
    return calculationOutput.sentences
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
      (it.offence.committedAt?.isAfterOrEqualTo(SHPO_BREACH_OFFENCE_FROM_DATE) == true && it.offence.committedAt.isBeforeOrEqualTo(trancheConfiguration.trancheThreeCommencementDate))
  }

  /**
   * Only return where sentence is post SDS-40 commencement date AND
   * is either a Recall with a top-up supervision date OR the sentence is not a Recall
   * AND the release date is before the Tranche 3 commencement date
   *
   * All releases post Tranche 3 commencement date are SDS-50
   */
  private fun filterSXOffenceDates(sentence: CalculableSentence): Boolean = sentence.sentenceCalculation.adjustedDeterminateReleaseDate.isAfterOrEqualTo(trancheConfiguration.trancheOneCommencementDate) &&
    sentence.sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(trancheConfiguration.trancheThreeCommencementDate) &&
    (!sentence.isRecall() || sentence.sentenceCalculation.topUpSupervisionDate != null)

  override fun validationOrder() = ValidationOrder.UNSUPPORTED

  companion object {
    private const val TWELVE = 12L
    private val shpoSX03OffenceCodes = listOf("SX03220", "SX03220A", "SX03244", "SX03244A", "SX03247", "SX03247A")
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
