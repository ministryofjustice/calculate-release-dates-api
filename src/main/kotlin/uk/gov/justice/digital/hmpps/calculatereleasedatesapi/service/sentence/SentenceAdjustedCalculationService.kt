package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.IMMEDIATE_RELEASE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.LED_CONSEC_ORA_AND_NON_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NCRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NPD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BotusSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ErsedCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.HdcedCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ReleaseMultiplier
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.TusedCalculator
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import kotlin.collections.set

@Service
class SentenceAdjustedCalculationService(
  val tusedCalculator: TusedCalculator,
  val hdcedCalculator: HdcedCalculator,
  val ersedCalculator: ErsedCalculator,
) {
  /*
    This function calculates dates after adjustments have been decided.
    It can be run many times to recalculate dates. It needs to be run if there is a change to adjustments.
   */
  fun calculateDatesFromAdjustments(sentence: CalculableSentence, offender: Offender): SentenceCalculation {
    val sentenceCalculation: SentenceCalculation = sentence.sentenceCalculation
    // Other adjustments need to be included in the sentence calculation here
    setCrdOrArdDetails(sentence, sentenceCalculation)
    setSedOrSledDetails(sentence, sentenceCalculation)
    setPedDetails(sentence, sentenceCalculation)

    if (sentenceCalculation.calculateErsed) {
      ersedCalculator.generateEarlyReleaseSchemeEligibilityDateBreakdown(sentence, sentenceCalculation)
    }

    if (sentence is BotusSentence) {
      tusedCalculator.updateBotusSentenceTused(sentence)
    } else {
      determineTUSED(sentenceCalculation, sentence, offender)
    }

    if (sentence.releaseDateTypes.contains(ReleaseDateType.ETD) && !sentenceCalculation.isImmediateRelease()) {
      if (sentence.durationIsGreaterThanOrEqualTo(8, MONTHS) && sentence.durationIsLessThan(18, MONTHS)) {
        sentenceCalculation.earlyTransferDate = sentenceCalculation.releaseDate.minusMonths(1)
      } else if (sentence.durationIsGreaterThanOrEqualTo(18, MONTHS) &&
        sentence.durationIsLessThanEqualTo(
          24,
          MONTHS,
        )
      ) {
        sentenceCalculation.earlyTransferDate = sentenceCalculation.releaseDate.minusMonths(2)
      }
    }

    if (sentence.releaseDateTypes.contains(ReleaseDateType.LTD) && !sentenceCalculation.isImmediateRelease()) {
      if (sentence.durationIsGreaterThanOrEqualTo(8, MONTHS) && sentence.durationIsLessThan(18, MONTHS)) {
        sentenceCalculation.latestTransferDate = sentenceCalculation.releaseDate.plusMonths(1)
      } else if (sentence.durationIsGreaterThanOrEqualTo(18, MONTHS) &&
        sentence.durationIsLessThanEqualTo(
          24,
          MONTHS,
        )
      ) {
        sentenceCalculation.latestTransferDate = sentenceCalculation.releaseDate.plusMonths(2)
      }
    }

    if (sentence.releaseDateTypes.contains(NPD)) {
      if (sentence.releaseDateTypes.contains(NCRD)) {
        calculateNPDFromNotionalCRD(sentence, sentenceCalculation)
      } else {
        calculateNPD(sentenceCalculation, sentence)
      }
    }

    if (sentence.releaseDateTypes.contains(LED)) {
      calculateLED(sentence, sentenceCalculation)
    }

    if (sentence.releaseDateTypes.contains(HDCED)) {
      hdcedCalculator.calculateHdced(sentence, sentenceCalculation)
    }
    log.trace(sentence.buildString())
    return sentenceCalculation
  }

  private fun setPedDetails(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ) {
    if (sentence.releaseDateTypes.contains(PED) && sentenceCalculation.extendedDeterminateParoleEligibilityDate != null) {
      sentenceCalculation.breakdownByReleaseDateType[PED] = ReleaseDateCalculationBreakdown(
        releaseDate = sentenceCalculation.extendedDeterminateParoleEligibilityDate!!,
        unadjustedDate = sentenceCalculation.unadjustedExtendedDeterminateParoleEligibilityDate!!,
        adjustedDays = DAYS.between(
          sentenceCalculation.unadjustedExtendedDeterminateParoleEligibilityDate,
          sentenceCalculation.extendedDeterminateParoleEligibilityDate,
        ),
      )
    }
  }

  private fun setSedOrSledDetails(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ) {
    if (sentence.releaseDateTypes.contains(SLED)) {
      sentenceCalculation.breakdownByReleaseDateType[SLED] = getBreakdownForExpiryDate(sentenceCalculation)
    } else {
      sentenceCalculation.breakdownByReleaseDateType[SED] = getBreakdownForExpiryDate(sentenceCalculation)
    }
  }

  private fun setCrdOrArdDetails(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ) {
    if (sentence.releaseDateTypes.contains(ARD)) {
      sentenceCalculation.isReleaseDateConditional = false
      sentenceCalculation.breakdownByReleaseDateType[ARD] = getBreakdownForReleaseDate(sentenceCalculation)
    } else if (sentence.releaseDateTypes.contains(CRD)) {
      sentenceCalculation.isReleaseDateConditional = true
      sentenceCalculation.breakdownByReleaseDateType[CRD] = getBreakdownForReleaseDate(sentenceCalculation)
    }
  }

  private fun getBreakdownForExpiryDate(sentenceCalculation: SentenceCalculation) = ReleaseDateCalculationBreakdown(
    releaseDate = sentenceCalculation.adjustedExpiryDate,
    unadjustedDate = sentenceCalculation.unadjustedExpiryDate,
    adjustedDays = DAYS.between(
      sentenceCalculation.unadjustedExpiryDate,
      sentenceCalculation.adjustedExpiryDate,
    ),
  )

  private fun getBreakdownForReleaseDate(sentenceCalculation: SentenceCalculation): ReleaseDateCalculationBreakdown {
    val daysBetween = DAYS.between(
      sentenceCalculation.unadjustedDeterminateReleaseDate,
      sentenceCalculation.adjustedDeterminateReleaseDate,
    )
    return ReleaseDateCalculationBreakdown(
      releaseDate = sentenceCalculation.adjustedDeterminateReleaseDate,
      unadjustedDate = sentenceCalculation.unadjustedDeterminateReleaseDate,
      rules = if (sentenceCalculation.isImmediateRelease()) setOf(IMMEDIATE_RELEASE) else emptySet(),
      adjustedDays = daysBetween,
      rulesWithExtraAdjustments = if (sentenceCalculation.adjustments.unusedAdaDays != 0L) {
        mapOf(
          CalculationRule.UNUSED_ADA to AdjustmentDuration(
            sentenceCalculation.adjustments.unusedAdaDays,
            DAYS,
          ),
        )
      } else {
        emptyMap()
      },
    )
  }

  private fun calculateLED(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
  ) {
    if (sentence is ConsecutiveSentence &&
      sentence.isMadeUpOfOnlyAfterCjaLaspoSentences() &&
      sentence.hasOraSentences() &&
      sentence.hasNonOraSentences()
    ) {
      val lengthOfOraSentences =
        sentence.orderedSentences.filter { it is StandardDeterminateSentence && it.isOraSentence() }
          .map { (it as StandardDeterminateSentence).duration }
          .reduce { acc, duration -> acc.appendAll(duration.durationElements) }
          .getLengthInDays(sentence.sentencedAt)
      val adjustment = ReleaseMultiplier.ONE_HALF.applyTo(lengthOfOraSentences, RoundingMode.FLOOR)
      sentenceCalculation.licenceExpiryDate =
        sentenceCalculation.adjustedDeterminateReleaseDate
          .plusDays(adjustment)
          .minusDays(sentenceCalculation.adjustments.unusedLicenceAdaDays)
      sentenceCalculation.numberOfDaysToLicenceExpiryDate =
        DAYS.between(sentence.sentencedAt, sentenceCalculation.licenceExpiryDate)
      // The LED is calculated from the adjusted release date, therefore unused ADA from the release date has also been applied.
      val unusedAda =
        sentenceCalculation.adjustments.unusedAdaDays + sentenceCalculation.adjustments.unusedLicenceAdaDays
      sentenceCalculation.breakdownByReleaseDateType[LED] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(LED_CONSEC_ORA_AND_NON_ORA),
          adjustedDays = adjustment,
          releaseDate = sentenceCalculation.licenceExpiryDate!!,
          unadjustedDate = sentenceCalculation.adjustedDeterminateReleaseDate,
          rulesWithExtraAdjustments = if (unusedAda != 0L) {
            mapOf(
              CalculationRule.UNUSED_ADA to AdjustmentDuration(
                unusedAda,
                DAYS,
              ),
            )
          } else {
            emptyMap()
          },
        )
    } else {
      sentenceCalculation.numberOfDaysToLicenceExpiryDate =
        ReleaseMultiplier.THREE_QUARTERS.applyTo(sentenceCalculation.numberOfDaysToSentenceExpiryDate)
          .plus(sentenceCalculation.numberOfDaysToAddToLicenceExpiryDate)
          .plus(sentenceCalculation.adjustments.adjustmentsForInitialReleaseWithoutAwarded())
      sentenceCalculation.licenceExpiryDate = sentence.sentencedAt.plusDays(
        sentenceCalculation.numberOfDaysToLicenceExpiryDate,
      ).minusDays(ONE)
    }
  }

  private fun calculateNPD(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
  ) {
    sentenceCalculation.numberOfDaysToNonParoleDate = ReleaseMultiplier.TWO_THIRDS.applyTo(sentenceCalculation.numberOfDaysToSentenceExpiryDate)
      .plus(sentenceCalculation.adjustments.adjustmentsForInitialReleaseWithoutAwarded())
    sentenceCalculation.nonParoleDate = sentence.sentencedAt.plusDays(
      sentenceCalculation.numberOfDaysToNonParoleDate,
    ).minusDays(ONE)
  }

  private fun determineTUSED(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    offender: Offender,
  ) {
    if (!tusedCalculator.sentenceMatchesTusedCriteria(sentence, offender)) {
      sentenceCalculation.topUpSupervisionDate = null
      sentenceCalculation.breakdownByReleaseDateType.remove(TUSED)
      return
    }

    val tused = tusedCalculator.calculateTused(sentenceCalculation)

    if (tused is LocalDate) {
      sentenceCalculation.topUpSupervisionDate = tused
      sentenceCalculation.breakdownByReleaseDateType[TUSED] =
        tusedCalculator.getCalculationBreakdown(sentenceCalculation)
    } else {
      sentenceCalculation.topUpSupervisionDate = null
      sentenceCalculation.breakdownByReleaseDateType.remove(TUSED)
    }
  }

  // If a sentence needs to calculate an NPD, but it is an aggregated sentence made up of "old" and "new" type sentences
  // The NPD calc becomes much more complicated, see PSI example 40.
  private fun calculateNPDFromNotionalCRD(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation) {
    if (sentence is ConsecutiveSentence &&
      sentence.allSentencesAreStandardSentences()
    ) {
      val daysOfNewStyleSentences = sentence.orderedSentences
        .filter { it is StandardDeterminateSentence && it.isAfterCJAAndLASPO() }
        .map { (it as StandardDeterminateSentence).duration }
        .reduce { acc, duration -> acc.appendAll(duration.durationElements) }
        .getLengthInDays(sentence.sentencedAt)

      val daysOfOldStyleSentences = sentence.orderedSentences
        .filter { it is StandardDeterminateSentence && it.isBeforeCJAAndLASPO() }
        .map { (it as StandardDeterminateSentence).duration }
        .reduce { acc, duration -> acc.appendAll(duration.durationElements) }
        .getLengthInDays(sentence.sentencedAt)

      sentenceCalculation.numberOfDaysToNotionalConditionalReleaseDate = ReleaseMultiplier.ONE_HALF.applyTo(daysOfNewStyleSentences)

      val unAdjustedNotionalConditionalReleaseDate = sentence.sentencedAt
        .plusDays(sentenceCalculation.numberOfDaysToNotionalConditionalReleaseDate)
        .minusDays(ONE)

      sentenceCalculation.notionalConditionalReleaseDate = unAdjustedNotionalConditionalReleaseDate.plusDays(
        sentenceCalculation.adjustments.adjustmentsForInitialRelease(),
      )

      val dayAfterNotionalConditionalReleaseDate =
        sentenceCalculation.notionalConditionalReleaseDate!!.plusDays(ONE)
      sentenceCalculation.numberOfDaysToNonParoleDate = ReleaseMultiplier.TWO_THIRDS.applyTo(daysOfOldStyleSentences)
      sentenceCalculation.nonParoleDate = dayAfterNotionalConditionalReleaseDate
        .plusDays(sentenceCalculation.numberOfDaysToNonParoleDate)
        .minusDays(ONE)
    }
  }

  companion object {
    private const val ONE = 1L
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
