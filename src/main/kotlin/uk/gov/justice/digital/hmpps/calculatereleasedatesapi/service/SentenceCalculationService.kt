package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.HDCED_GE_12W_LT_18M
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.HDCED_GE_18M_LT_4Y
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.HDCED_MINIMUM_14D
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.IdentifiableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

@Service
class SentenceCalculationService {

  fun calculate(sentence: CalculableSentence, booking: Booking): SentenceCalculation {
    val sentenceCalculation = getInitialCalculation(sentence, booking, sentence.sentencedAt)
    // create association between the sentence and it's calculation
    sentence.sentenceCalculation = sentenceCalculation
    return calculateDatesFromAdjustments(sentence)
  }

  private fun getInitialCalculation(
    sentence: CalculableSentence,
    booking: Booking,
    adjustmentsFrom: LocalDate
  ): SentenceCalculation {
    val releaseDateMultiplier = determineReleaseDateMultiplier(sentence)
    // create the intermediate values
    val numberOfDaysToSentenceExpiryDate = sentence.getLengthInDays()

    val numberOfDaysToReleaseDateDouble = numberOfDaysToSentenceExpiryDate.toDouble().times(releaseDateMultiplier)
    val numberOfDaysToReleaseDate = if (sentence is ConsecutiveSentence && sentence.isMadeUpOfSdsPlusAndSdsSentences()) {
      ceil(
        sentence.orderedSentences.map { it.sentenceCalculation.numberOfDaysToReleaseDateDouble }
          .reduce { acc, it -> acc + it }
      )
        .toInt()
    } else {
      ceil(numberOfDaysToReleaseDateDouble).toInt()
    }

    val unadjustedExpiryDate =
      sentence.sentencedAt
        .plusDays(numberOfDaysToSentenceExpiryDate.toLong())
        .minusDays(ONE)

    val unadjustedReleaseDate =
      sentence.sentencedAt
        .plusDays(numberOfDaysToReleaseDate.toLong())
        .minusDays(ONE)

    // create new SentenceCalculation and associate it with a sentence
    return SentenceCalculation(
      sentence,
      numberOfDaysToSentenceExpiryDate,
      numberOfDaysToReleaseDateDouble,
      numberOfDaysToReleaseDate,
      unadjustedExpiryDate,
      unadjustedReleaseDate,
      booking.adjustments,
      sentence.sentencedAt
    )
  }

  /*
    This function calculates dates after adjustments have been decided.
    It can be run many times to recalculate dates. It needs to be run if there is a change to adjustments.
   */
  fun calculateDatesFromAdjustments(sentence: CalculableSentence): SentenceCalculation {
    val sentenceCalculation: SentenceCalculation = sentence.sentenceCalculation
    // Other adjustments need to be included in the sentence calculation here
    setCrdOrArdDetails(sentence, sentenceCalculation)
    setSedOrSledDetails(sentence, sentenceCalculation)

    // PSI 03/2015: P53: The license period is one of at least 12 month.
    // Hence, there is no requirement for a TUSED
    if (sentenceCalculation.numberOfDaysToSentenceExpiryDate - sentenceCalculation.numberOfDaysToReleaseDate < YEAR_IN_DAYS && sentence.releaseDateTypes.contains(
        TUSED
      )
    ) {
      calculateTUSED(sentenceCalculation)
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
      adjustedDays = DAYS.between(sentenceCalculation.unadjustedExpiryDate, sentenceCalculation.adjustedExpiryDate)
        .toInt()
    )

  private fun getBreakdownForReleaseDate(sentenceCalculation: SentenceCalculation) =
    ReleaseDateCalculationBreakdown(
      releaseDate = sentenceCalculation.adjustedReleaseDate,
      unadjustedDate = sentenceCalculation.unadjustedReleaseDate,
      adjustedDays = DAYS.between(sentenceCalculation.unadjustedReleaseDate, sentenceCalculation.adjustedReleaseDate)
        .toInt()
    )

  private fun calculateLED(
    sentence: CalculableSentence,
    sentenceCalculation: SentenceCalculation
  ) {
    if (sentence is ConsecutiveSentence &&
      sentence.isMadeUpOfOnlyAfterCjaLaspoSentences() &&
      sentence.hasOraSentences() &&
      sentence.hasNonOraSentences()
    ) {
      val lengthOfOraSentences = sentence.orderedSentences.filter(Sentence::isOraSentence)
        .map { it.duration.copy() }
        .reduce { acc, duration -> acc.appendAll(duration.durationElements) }
        .getLengthInDays(sentence.sentencedAt)
      val adjustment = floor(lengthOfOraSentences.toDouble().div(TWO)).toLong()
      sentenceCalculation.licenceExpiryDate =
        sentenceCalculation.releaseDate!!.plusDays(adjustment)
      sentenceCalculation.numberOfDaysToLicenceExpiryDate =
        DAYS.between(sentence.sentencedAt, sentenceCalculation.licenceExpiryDate)
      sentenceCalculation.breakdownByReleaseDateType[LED] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(LED_CONSEC_ORA_AND_NON_ORA),
          adjustedDays = adjustment.toInt(),
          releaseDate = sentenceCalculation.licenceExpiryDate!!,
          unadjustedDate = sentenceCalculation.releaseDate!!
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
    sentenceCalculation.topUpSupervisionDate = sentenceCalculation.unadjustedReleaseDate
      .plus(TWELVE, MONTHS).plusDays(adjustedDays.toLong())
    sentenceCalculation.breakdownByReleaseDateType[TUSED] =
      ReleaseDateCalculationBreakdown(
        rules = setOf(TUSED_LICENCE_PERIOD_LT_1Y),
        rulesWithExtraAdjustments = mapOf(TUSED_LICENCE_PERIOD_LT_1Y to AdjustmentDuration(TWELVE.toInt(), MONTHS)),
        adjustedDays = adjustedDays,
        releaseDate = sentenceCalculation.topUpSupervisionDate!!,
        unadjustedDate = sentenceCalculation.unadjustedReleaseDate,
      )
  }

  // If a sentence needs to calculate an NPD, but it is an aggregated sentence made up of "old" and "new" type sentences
  // The NPD calc becomes much more complicated, see PSI example 40.
  private fun calculateNPDFromNotionalCRD(sentence: CalculableSentence, sentenceCalculation: SentenceCalculation) {
    if (sentence is ConsecutiveSentence) {
      val daysOfNewStyleSentences = sentence.orderedSentences
        .filter { it.identificationTrack == SentenceIdentificationTrack.SDS_AFTER_CJA_LASPO }
        .map { it.duration.copy() }
        .reduce { acc, duration -> acc.appendAll(duration.durationElements) }
        .getLengthInDays(sentence.sentencedAt)

      val daysOfOldStyleSentences = sentence.orderedSentences
        .filter { it.identificationTrack == SentenceIdentificationTrack.SDS_BEFORE_CJA_LASPO }
        .map { it.duration.copy() }
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

      val dayAfterNotionalConditionalReleaseDate = sentenceCalculation.notionalConditionalReleaseDate!!.plusDays(ONE)
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
    if (sentenceCalculation.adjustedReleaseDate.isBefore(sentence.sentencedAt.plusDays(14))) {
      sentenceCalculation.homeDetentionCurfewEligibilityDate = null
      return
    }

    val adjustedDays = sentenceCalculation.calculatedTotalAddedDays
      .plus(sentenceCalculation.calculatedTotalAwardedDays)
      .minus(sentenceCalculation.calculatedTotalDeductedDays)

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
    val twentyEightOrMore = max(TWENTY_EIGHT, ceil(sentenceCalculation.numberOfDaysToSentenceExpiryDate.toDouble().div(FOUR)).toLong())

    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = twentyEightOrMore.plus(adjustedDays)
    sentenceCalculation.homeDetentionCurfewEligibilityDate = sentence.sentencedAt.plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate)

    if (sentence.sentencedAt.plusDays(FOURTEEN).isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfewEligibilityDate!!)) {
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
    sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate = sentenceCalculation.numberOfDaysToReleaseDate
      .minus(ONE_HUNDRED_AND_THIRTY_FIVE).toLong()
      .plus(adjustedDays)
    sentenceCalculation.homeDetentionCurfewEligibilityDate = sentence.sentencedAt
      .plusDays(sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate)

    if (sentence.sentencedAt.plusDays(FOURTEEN).isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfewEligibilityDate!!)) {
      calculateHDCEDFourteenDays(sentence, sentenceCalculation, HDCED_GE_18M_LT_4Y)
    } else {
      sentenceCalculation.breakdownByReleaseDateType[HDCED] =
        ReleaseDateCalculationBreakdown(
          rules = setOf(HDCED_GE_18M_LT_4Y),
          rulesWithExtraAdjustments = mapOf(HDCED_GE_18M_LT_4Y to AdjustmentDuration(-ONE_HUNDRED_AND_THIRTY_FIVE)),
          adjustedDays = adjustedDays,
          releaseDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!,
          unadjustedDate = sentenceCalculation.homeDetentionCurfewEligibilityDate!!.plusDays(ONE_HUNDRED_AND_THIRTY_FIVE.toLong())
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

  private fun determineReleaseDateMultiplier(sentence: CalculableSentence): Double {
    return if (
      (sentence is IdentifiableSentence) &&
      sentence.identificationTrack == SentenceIdentificationTrack.SDS_PLUS
    ) {
      2 / 3.toDouble()
    } else {
      1 / 2.toDouble()
    }
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
