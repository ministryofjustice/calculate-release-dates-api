package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.HDCED_GE_12W_LT_18M
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.HDCED_GE_18M_LT_4Y
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.HDCED_MINIMUM_14D
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.IMMEDIATE_RELEASE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.LED_CONSEC_ORA_AND_NON_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.TUSED_LICENCE_PERIOD_LT_1Y
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NCRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NPD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentDuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

@Service
class SentenceAdjustedCalculationService {
    /*
      This function calculates dates after adjustments have been decided.
      It can be run many times to recalculate dates. It needs to be run if there is a change to adjustments.
     */
  fun calculateDatesFromAdjustments(sentence: CalculableSentence, booking: Booking): SentenceCalculation {
    val sentenceCalculation: SentenceCalculation = sentence.sentenceCalculation
    // Other adjustments need to be included in the sentence calculation here
    setCrdOrArdDetails(sentence, sentenceCalculation)
    setSedOrSledDetails(sentence, sentenceCalculation)

    // PSI 03/2015: P53: The license period is one of at least 12 month.
    // Hence, there is no requirement for a TUSED
    if (sentenceCalculation.numberOfDaysToSentenceExpiryDate - sentenceCalculation.numberOfDaysToDeterminateReleaseDate < YEAR_IN_DAYS && sentence.releaseDateTypes.contains(
        TUSED
      )
    ) {
      if (booking.offender.getAgeOnDate(sentence.sentenceCalculation.releaseDateWithoutAwarded) >= 18) {
        calculateTUSED(sentenceCalculation)
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
      calculateHDCED(sentence, sentenceCalculation)
    }

    BookingCalculationService.log.info(sentence.buildString())
    return sentenceCalculation
  }

  private fun setSedOrSledDetails(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation
  ) {
    if (sentence.releaseDateTypes.contains(SLED)) {
      sentenceCalculation.breakdownByReleaseDateType[SLED] = getBreakdownForExpiryDate(sentenceCalculation)
    } else {
      sentenceCalculation.breakdownByReleaseDateType[SED] = getBreakdownForExpiryDate(sentenceCalculation)
    }
  }

  private fun setCrdOrArdDetails(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation
  ) {
    if (sentence.releaseDateTypes.contains(ARD)) {
      sentenceCalculation.isReleaseDateConditional = false
      sentenceCalculation.breakdownByReleaseDateType[ARD] = getBreakdownForReleaseDate(sentenceCalculation)
    } else if (sentence.releaseDateTypes.contains(CRD)) {
      sentenceCalculation.isReleaseDateConditional = true
      sentenceCalculation.breakdownByReleaseDateType[CRD] = getBreakdownForReleaseDate(sentenceCalculation)
    }
  }

  private fun getBreakdownForExpiryDate(sentenceCalculation: SentenceCalculation) =
    ReleaseDateCalculationBreakdown(
      releaseDate = sentenceCalculation.adjustedExpiryDate,
      unadjustedDate = sentenceCalculation.unadjustedExpiryDate,
      adjustedDays = DAYS.between(
        sentenceCalculation.unadjustedExpiryDate,
        sentenceCalculation.adjustedExpiryDate
      )
        .toInt()
    )

  private fun getBreakdownForReleaseDate(sentenceCalculation: SentenceCalculation): ReleaseDateCalculationBreakdown {
    val immediateRelease = sentenceCalculation.sentence.sentencedAt == sentenceCalculation.releaseDate
    val daysBetween = DAYS.between(
      sentenceCalculation.unadjustedDeterminateReleaseDate,
      sentenceCalculation.adjustedDeterminateReleaseDate
    )
      .toInt()
    return ReleaseDateCalculationBreakdown(
      releaseDate = sentenceCalculation.adjustedDeterminateReleaseDate,
      unadjustedDate = sentenceCalculation.unadjustedDeterminateReleaseDate,
      rules = if (immediateRelease) setOf(IMMEDIATE_RELEASE) else emptySet(),
      adjustedDays = daysBetween,
      rulesWithExtraAdjustments = if (sentenceCalculation.calculatedUnusedReleaseAda != 0) mapOf(
        CalculationRule.UNUSED_ADA to AdjustmentDuration(
          sentenceCalculation.calculatedUnusedReleaseAda,
          DAYS
        )
      ) else emptyMap()
    )
  }

  private fun calculateLED(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation
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
      val adjustment = floor(lengthOfOraSentences.toDouble().div(TWO)).toLong()
      sentenceCalculation.licenceExpiryDate =
        sentenceCalculation.adjustedDeterminateReleaseDate
          .plusDays(adjustment)
          .minusDays(sentenceCalculation.calculatedUnusedLicenseAda.toLong())
      sentenceCalculation.numberOfDaysToLicenceExpiryDate =
        DAYS.between(sentence.sentencedAt, sentenceCalculation.licenceExpiryDate)
      // The LED is calculated from the adjusted release date, therefore unused ADA from the release date has also been applied.
      val unusedAda =
        sentenceCalculation.calculatedUnusedReleaseAda + sentenceCalculation.calculatedUnusedLicenseAda
      sentenceCalculation.breakdownByReleaseDateType[LED] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(LED_CONSEC_ORA_AND_NON_ORA),
          adjustedDays = adjustment.toInt(),
          releaseDate = sentenceCalculation.licenceExpiryDate!!,
          unadjustedDate = sentenceCalculation.adjustedDeterminateReleaseDate,
          rulesWithExtraAdjustments = if (unusedAda != 0) mapOf(
            CalculationRule.UNUSED_ADA to AdjustmentDuration(
              unusedAda,
              DAYS
            )
          ) else emptyMap()
        )
    } else {
      sentenceCalculation.numberOfDaysToLicenceExpiryDate =
        ceil(sentenceCalculation.numberOfDaysToSentenceExpiryDate.toDouble().times(THREE).div(FOUR)).toLong()
          .plus(sentenceCalculation.numberOfDaysToAddToLicenceExpiryDate)
          .plus(sentenceCalculation.calculatedTotalAddedDays)
          .minus(sentenceCalculation.calculatedTotalDeductedDays)
      sentenceCalculation.licenceExpiryDate = sentence.sentencedAt.plusDays(
        sentenceCalculation.numberOfDaysToLicenceExpiryDate
      ).minusDays(ONE)
    }
  }

  private fun calculateNPD(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence
  ) {
    sentenceCalculation.numberOfDaysToNonParoleDate =
      ceil(sentenceCalculation.numberOfDaysToSentenceExpiryDate.toDouble().times(TWO).div(THREE)).toLong()
        .plus(sentenceCalculation.calculatedTotalAddedDays)
        .minus(sentenceCalculation.calculatedTotalDeductedDays)
    sentenceCalculation.nonParoleDate = sentence.sentencedAt.plusDays(
      sentenceCalculation.numberOfDaysToNonParoleDate
    ).minusDays(ONE)
  }

  private fun calculateTUSED(sentenceCalculation: SentenceCalculation) {
    val adjustedDays =
      sentenceCalculation.calculatedTotalAddedDays.minus(sentenceCalculation.calculatedTotalDeductedDays)
    val immediateRelease = sentenceCalculation.sentence.sentencedAt == sentenceCalculation.adjustedDeterminateReleaseDate
    if (immediateRelease) {
      //There may still be adjustments to consider here. If the immediate release occured and then there was a recall,
      //Any UAL after the recall will need to be added.
      val adjustedDaysAfterRelease = sentenceCalculation.getTotalAddedDaysAfter(sentenceCalculation.sentence.sentencedAt)
      sentenceCalculation.topUpSupervisionDate = sentenceCalculation.sentence.sentencedAt
        .plusDays(adjustedDaysAfterRelease.toLong())
        .plusMonths(TWELVE)
    } else {
      sentenceCalculation.topUpSupervisionDate = sentenceCalculation.unadjustedDeterminateReleaseDate
        .plusDays(adjustedDays.toLong())
        .plusMonths(TWELVE)
    }
    sentenceCalculation.breakdownByReleaseDateType[TUSED] =
      ReleaseDateCalculationBreakdown(
        rules = setOf(TUSED_LICENCE_PERIOD_LT_1Y) + if (immediateRelease) setOf(IMMEDIATE_RELEASE) else emptySet(),
        rulesWithExtraAdjustments = mapOf(
          TUSED_LICENCE_PERIOD_LT_1Y to AdjustmentDuration(
            TWELVE.toInt(),
            MONTHS
          )
        ),
        adjustedDays = adjustedDays,
        releaseDate = sentenceCalculation.topUpSupervisionDate!!,
        unadjustedDate = sentenceCalculation.unadjustedDeterminateReleaseDate,
      )
  }

  // If a sentence needs to calculate an NPD, but it is an aggregated sentence made up of "old" and "new" type sentences
  // The NPD calc becomes much more complicated, see PSI example 40.
  private fun calculateNPDFromNotionalCRD(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation) {
    if (sentence is ConsecutiveSentence &&
      sentence.allSentencesAreStandardSentences()
    ) {
      val daysOfNewStyleSentences = sentence.orderedSentences
        .filter { it is StandardDeterminateSentence && it.identificationTrack == SentenceIdentificationTrack.SDS_AFTER_CJA_LASPO }
        .map { (it as StandardDeterminateSentence).duration }
        .reduce { acc, duration -> acc.appendAll(duration.durationElements) }
        .getLengthInDays(sentence.sentencedAt)

      val daysOfOldStyleSentences = sentence.orderedSentences
        .filter { it is StandardDeterminateSentence && it.identificationTrack == SentenceIdentificationTrack.SDS_BEFORE_CJA_LASPO }
        .map { (it as StandardDeterminateSentence).duration }
        .reduce { acc, duration -> acc.appendAll(duration.durationElements) }
        .getLengthInDays(sentence.sentencedAt)

      sentenceCalculation.numberOfDaysToNotionalConditionalReleaseDate =
        ceil(daysOfNewStyleSentences.toDouble().div(TWO)).toLong()

      val unAdjustedNotionalConditionalReleaseDate = sentence.sentencedAt
        .plusDays(sentenceCalculation.numberOfDaysToNotionalConditionalReleaseDate)
        .minusDays(ONE)

      sentenceCalculation.notionalConditionalReleaseDate = unAdjustedNotionalConditionalReleaseDate.minusDays(
        sentenceCalculation.calculatedTotalDeductedDays.toLong()
      ).plusDays(
        sentenceCalculation.calculatedTotalAddedDays.toLong()
      ).plusDays(
        sentenceCalculation.calculatedTotalAwardedDays.toLong()
      )

      val dayAfterNotionalConditionalReleaseDate =
        sentenceCalculation.notionalConditionalReleaseDate!!.plusDays(ONE)
      sentenceCalculation.numberOfDaysToNonParoleDate =
        ceil(daysOfOldStyleSentences.toDouble().times(TWO).div(THREE)).toLong()
      sentenceCalculation.nonParoleDate = dayAfterNotionalConditionalReleaseDate
        .plusDays(sentenceCalculation.numberOfDaysToNonParoleDate)
        .minusDays(ONE)
    }
  }

  private fun calculateHDCED(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation) {
    // If adjustments make the CRD before sentence date plus 14 days (i.e. a large REMAND days)
    // then we don't need a HDCED date.
    if (sentenceCalculation.adjustedDeterminateReleaseDate.isBefore(sentence.sentencedAt.plusDays(14))) {
      sentenceCalculation.homeDetentionCurfewEligibilityDate = null
      return
    }

    val adjustedDays = sentenceCalculation.calculatedTotalAddedDays
      .plus(sentenceCalculation.calculatedTotalAwardedDays)
      .minus(sentenceCalculation.calculatedTotalDeductedDays)
      .minus(sentenceCalculation.calculatedUnusedReleaseAda)

    // Any sentences < 12W or >= 4Y have been excluded already in the identification service (no HDCED)
    if (sentence.durationIsLessThan(EIGHTEEN, MONTHS)) {
      calculateHDCEDLessThanEighteenMonths(sentenceCalculation, sentence, adjustedDays)
    } else {
      calculatedHDCEDLessThanFourYears(sentence, adjustedDays, sentenceCalculation)
    }
  }

  private fun calculateHDCEDLessThanEighteenMonths(
    sentenceCalculation: SentenceCalculation,
    sentence: CalculableSentence,
    adjustedDays: Int
  ) {
    val twentyEightOrMore =
      max(TWENTY_EIGHT, ceil(sentenceCalculation.numberOfDaysToSentenceExpiryDate.toDouble().div(FOUR)).toLong())

    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = twentyEightOrMore.plus(adjustedDays)
    sentenceCalculation.homeDetentionCurfewEligibilityDate =
      sentence.sentencedAt.plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate)

    if (sentence.sentencedAt.plusDays(FOURTEEN)
      .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfewEligibilityDate!!)
    ) {
      calculateHDCEDFourteenDays(sentence, sentenceCalculation, HDCED_GE_12W_LT_18M)
    } else {
      sentenceCalculation.breakdownByReleaseDateType[HDCED] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(HDCED_GE_12W_LT_18M),
          rulesWithExtraAdjustments = mapOf(HDCED_GE_12W_LT_18M to AdjustmentDuration(twentyEightOrMore.toInt())),
          adjustedDays = adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
          unadjustedDate = sentence.sentencedAt
        )
    }
  }

  private fun calculatedHDCEDLessThanFourYears(
    sentence: CalculableSentence,
    adjustedDays: Int,
    sentenceCalculation: SentenceCalculation
  ) {
    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate =
      sentenceCalculation.numberOfDaysToDeterminateReleaseDate
        .minus(ONE_HUNDRED_AND_THIRTY_FIVE).toLong()
        .plus(adjustedDays)
    sentenceCalculation.homeDetentionCurfewEligibilityDate = sentence.sentencedAt
      .plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate)

    if (sentence.sentencedAt.plusDays(FOURTEEN)
      .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfewEligibilityDate!!)
    ) {
      calculateHDCEDFourteenDays(sentence, sentenceCalculation, HDCED_GE_18M_LT_4Y)
    } else {
      sentenceCalculation.breakdownByReleaseDateType[HDCED] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(HDCED_GE_18M_LT_4Y),
          rulesWithExtraAdjustments = mapOf(HDCED_GE_18M_LT_4Y to AdjustmentDuration(-ONE_HUNDRED_AND_THIRTY_FIVE)),
          adjustedDays = adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
          unadjustedDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!.plusDays(
            ONE_HUNDRED_AND_THIRTY_FIVE.toLong()
          )
        )
    }
  }

  private fun calculateHDCEDFourteenDays(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation,
    parentRule: CalculationRule
  ) {
    sentenceCalculation.homeDetentionCurfewEligibilityDate = sentence.sentencedAt.plusDays(FOURTEEN)

    sentenceCalculation.breakdownByReleaseDateType[HDCED] =
      ReleaseDateCalculationBreakdown(
        rules = setOf(HDCED_MINIMUM_14D, parentRule),
        rulesWithExtraAdjustments = mapOf(HDCED_MINIMUM_14D to AdjustmentDuration(FOURTEEN.toInt())),
        adjustedDays = 0,
        releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
        unadjustedDate = sentence.sentencedAt
      )
  }

  companion object {
    private const val ONE = 1L
    private const val TWO = 2L
    private const val THREE = 3L
    private const val FOUR = 4L
    private const val TWELVE = 12L
    private const val FOURTEEN = 14L
    private const val EIGHTEEN = 18L
    private const val TWENTY_EIGHT = 28L
    private const val YEAR_IN_DAYS = 365
    private const val ONE_HUNDRED_AND_THIRTY_FIVE = 135
  }
}
