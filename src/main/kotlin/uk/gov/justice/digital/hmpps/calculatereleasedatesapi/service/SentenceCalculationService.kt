package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NCRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NPD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

@Service
class SentenceCalculationService {

  fun calculate(sentence: CalculableSentence, booking: Booking): SentenceCalculation {
    var firstSentenceCalc = getInitialCalculation(sentence, booking, sentence.sentencedAt)
    var adjustedSentenceCalc = getInitialCalculation(sentence, booking, firstSentenceCalc.adjustedReleaseDate)
    while (firstSentenceCalc.adjustedReleaseDate != adjustedSentenceCalc.adjustedReleaseDate) {
      firstSentenceCalc = adjustedSentenceCalc
      adjustedSentenceCalc = getInitialCalculation(sentence, booking, firstSentenceCalc.adjustedReleaseDate)
    }
    // create association between the sentence and it's calculation
    sentence.sentenceCalculation = adjustedSentenceCalc
    return calculateDatesFromAdjustments(sentence)
  }

  private fun getInitialCalculation(sentence: CalculableSentence, booking: Booking, adjustmentsFrom: LocalDate): SentenceCalculation {
    val releaseDateMultiplier = determineReleaseDateMultiplier(sentence)
    // create the intermediate values
    val numberOfDaysToSentenceExpiryDate = sentence.getLengthInDays()

    val numberOfDaysToReleaseDate =
      ceil(
        numberOfDaysToSentenceExpiryDate.toDouble().times(releaseDateMultiplier)
      ).toInt()

    val unadjustedExpiryDate =
      sentence.sentencedAt
        .plusDays(numberOfDaysToSentenceExpiryDate.toLong())
        .minusDays(ONE)

    val unadjustedReleaseDate =
      sentence.sentencedAt
        .plusDays(numberOfDaysToReleaseDate.toLong())
        .minusDays(ONE)

    val calculatedTotalDeductedDays =
      booking.getOrZero(AdjustmentType.REMAND, adjustmentsFrom) + booking.getOrZero(AdjustmentType.TAGGED_BAIL, adjustmentsFrom)

    val calculatedTotalAddedDays =
      booking.getOrZero(AdjustmentType.UNLAWFULLY_AT_LARGE, adjustmentsFrom)

    val calculatedTotalAwardedDays = max(
      0,
      booking.getOrZero(AdjustmentType.ADDITIONAL_DAYS_AWARDED, adjustmentsFrom) -
        booking.getOrZero(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED, adjustmentsFrom)
    )

    // create new SentenceCalculation and associate it with a sentence
    return SentenceCalculation(
      sentence,
      numberOfDaysToSentenceExpiryDate,
      numberOfDaysToReleaseDate,
      unadjustedExpiryDate,
      unadjustedReleaseDate,
      calculatedTotalDeductedDays,
      calculatedTotalAddedDays,
      calculatedTotalAwardedDays,
    )
  }

  /*
    This function calculates dates after adjustments have been decided.
    It can be run many times to recalculate dates. It needs to be run if there is a change to adjustments.
   */
  fun calculateDatesFromAdjustments(sentence: CalculableSentence): SentenceCalculation {
    val sentenceCalculation: SentenceCalculation = sentence.sentenceCalculation
    // Other adjustments need to be included in the sentence calculation here
    if (sentence.releaseDateTypes.contains(ARD)) {
      sentenceCalculation.isReleaseDateConditional = false
    } else if (sentence.releaseDateTypes.contains(CRD)) {
      sentenceCalculation.isReleaseDateConditional = true
    }

    if (
      (sentenceCalculation.numberOfDaysToSentenceExpiryDate - sentenceCalculation.numberOfDaysToReleaseDate)
      >= YEAR_IN_DAYS
    ) {
      // PSI 03/2015: P53: The license period is one of at least 12 month.
      // Hence, there is no requirement for a TUSED
    } else {
      if (sentence.releaseDateTypes.contains(TUSED)) {
        sentenceCalculation.topUpSupervisionDate = sentenceCalculation.unadjustedReleaseDate
          .plus(TWELVE, ChronoUnit.MONTHS).minusDays(
            sentenceCalculation.calculatedTotalDeductedDays.toLong()
          ).plusDays(
            sentenceCalculation.calculatedTotalAddedDays.toLong()
          )
      }
    }

    if (sentence.releaseDateTypes.contains(NPD)) {
      if (sentence.releaseDateTypes.contains(NCRD)) {
        calculateNPDFromNotionalCRD(sentence, sentenceCalculation)
      } else {
        sentenceCalculation.numberOfDaysToNonParoleDate =
          ceil(sentenceCalculation.numberOfDaysToSentenceExpiryDate.toDouble().times(TWO).div(THREE)).toLong()
            .plus(sentenceCalculation.calculatedTotalAddedDays)
            .minus(sentenceCalculation.calculatedTotalDeductedDays)
        sentenceCalculation.nonParoleDate = sentence.sentencedAt.plusDays(
          sentenceCalculation.numberOfDaysToNonParoleDate
        ).minusDays(ONE)
      }
    }

    if (sentence.releaseDateTypes.contains(LED)) {
      // PSI example 25
      if (sentence is ConsecutiveSentence &&
        sentence.isMadeUpOfOnlyAfterCjaLaspoSentences() &&
        sentence.hasOraSentences() &&
        sentence.hasNonOraSentences()
      ) {
        val lengthOfOraSentences = sentence.orderedSentences.filter(Sentence::isOraSentence)
          .map { it.duration.copy() }
          .reduce { acc, duration -> acc.appendAll(duration.durationElements) }
          .getLengthInDays(sentence.sentencedAt)

        sentenceCalculation.licenceExpiryDate = sentenceCalculation.releaseDate!!.plusDays(floor(lengthOfOraSentences.toDouble().div(TWO)).toLong())
        sentenceCalculation.numberOfDaysToLicenceExpiryDate = ChronoUnit.DAYS.between(sentence.sentencedAt, sentenceCalculation.licenceExpiryDate)
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

    if (sentence.releaseDateTypes.contains(HDCED)) {
      calculateHDCED(sentence, sentenceCalculation)
    }

    BookingCalculationService.log.info(sentence.buildString())
    return sentenceCalculation
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
    if (sentence.durationIsLessThan(EIGHTEEN, ChronoUnit.MONTHS)) {
      sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate =
        max(TWENTY_EIGHT, ceil(sentenceCalculation.numberOfDaysToSentenceExpiryDate.toDouble().div(FOUR)).toLong())
          .plus(sentenceCalculation.calculatedTotalAddedDays)
          .minus(sentenceCalculation.calculatedTotalDeductedDays)
          .plus(sentenceCalculation.calculatedTotalAwardedDays)
      sentenceCalculation.homeDetentionCurfewEligibilityDate = sentence.sentencedAt.plusDays(
        sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate
      )
    } else {
      sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate =
        sentenceCalculation.numberOfDaysToReleaseDate.minus(ONE_HUNDRED_AND_THIRTY_FOUR).toLong()
          .plus(sentenceCalculation.calculatedTotalAddedDays)
          .minus(sentenceCalculation.calculatedTotalDeductedDays)
          .plus(sentenceCalculation.calculatedTotalAwardedDays)
      sentenceCalculation.homeDetentionCurfewEligibilityDate = sentence.sentencedAt.plusDays(
        sentenceCalculation.numberOfDaysToHomeDetentionCurfewEligibilityDate
      ).minusDays(ONE)
    }
    // If adjustments make the CRD before sentence date (i.e. a large REMAND days)
    // then we don't need a HDCED date.
    if (sentence.sentencedAt.isAfterOrEqualTo(sentenceCalculation.adjustedReleaseDate)) {
      sentenceCalculation.homeDetentionCurfewEligibilityDate = null
    } else if (sentence.sentencedAt.plusDays(FOURTEEN)
      .isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfewEligibilityDate!!)
    ) {
      sentenceCalculation.homeDetentionCurfewEligibilityDate = sentence.sentencedAt.plusDays(FOURTEEN)
    }
  }

  private fun determineReleaseDateMultiplier(sentence: CalculableSentence): Double {
    return if (
      (sentence is Sentence) &&
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
    private const val ONE_HUNDRED_AND_THIRTY_FOUR = 134
  }
}
