package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max

@Service
class SentenceCalculationService {

  fun calculate(sentence: Sentence, booking: Booking): SentenceCalculation {

    val sentenceCalculation = getInitialCalculation(sentence, booking)
    // Other adjustments need to be included in the sentence calculation here

    if (sentence.sentenceTypes.contains(SentenceType.SLED) || sentence.sentenceTypes.contains(SentenceType.SED)) {
      sentenceCalculation.expiryDate = sentenceCalculation.adjustedExpiryDate
    }

    if (sentence.sentenceTypes.contains(SentenceType.SLED)) {
      sentenceCalculation.licenceExpiryDate = sentenceCalculation.adjustedExpiryDate
    }

    if (sentence.sentenceTypes.contains(SentenceType.ARD)) {
      sentenceCalculation.isReleaseDateConditional = false
    } else if (sentence.sentenceTypes.contains(SentenceType.CRD)) {
      sentenceCalculation.isReleaseDateConditional = true
    }

    if (
      sentence.sentenceTypes.contains(SentenceType.CRD) ||
      sentence.sentenceTypes.contains(SentenceType.ARD) ||
      sentence.sentenceTypes.contains(SentenceType.PED)
    ) {
      sentenceCalculation.releaseDate = sentenceCalculation.adjustedReleaseDate
    }

    if (
      (sentenceCalculation.numberOfDaysToSentenceExpiryDate - sentenceCalculation.numberOfDaysToReleaseDate)
      >= YEAR_IN_DAYS
    ) {
      // PSI 03/2015: P53: The license period is one of at least 12 month.
      // Hence, there is no requirement for a TUSED
    } else {
      if (sentence.sentenceTypes.contains(SentenceType.TUSED)) {
        sentenceCalculation.topUpSupervisionDate = sentenceCalculation.unadjustedReleaseDate
          .plus(TWELVE, ChronoUnit.MONTHS).minusDays(
            sentenceCalculation.calculatedTotalDeductedDays.toLong()
          ).plusDays(
            sentenceCalculation.calculatedTotalAddedDays.toLong()
          )
      }
    }

    if (sentence.sentenceTypes.contains(SentenceType.NPD)) {
      sentenceCalculation.numberOfDaysToNonParoleDate =
        ceil(sentenceCalculation.numberOfDaysToSentenceExpiryDate.toDouble().times(TWO).div(THREE)).toLong()
          .plus(sentenceCalculation.calculatedTotalAddedDays)
          .minus(sentenceCalculation.calculatedTotalDeductedDays)
      sentenceCalculation.nonParoleDate = sentence.sentencedAt.plusDays(
        sentenceCalculation.numberOfDaysToNonParoleDate
      ).minusDays(ONE)
    }

    if (sentence.sentenceTypes.contains(SentenceType.LED)) {
      sentenceCalculation.numberOfDaysToLicenceExpiryDate =
        ceil(sentenceCalculation.numberOfDaysToSentenceExpiryDate.toDouble().times(THREE).div(FOUR)).toLong()
          .plus(sentenceCalculation.numberOfDaysToAddToLicenceExpiryDate)
          .plus(sentenceCalculation.calculatedTotalAddedDays)
          .minus(sentenceCalculation.calculatedTotalDeductedDays)
      sentenceCalculation.licenceExpiryDate = sentence.sentencedAt.plusDays(
        sentenceCalculation.numberOfDaysToLicenceExpiryDate
      ).minusDays(ONE)
    }

    if (sentence.sentenceTypes.contains(SentenceType.HDCED)) {
      calculateHDCED(sentence, sentenceCalculation)
    }

    // create association between the sentence and it's calculation
    sentence.sentenceCalculation = sentenceCalculation
    return sentenceCalculation
  }

  private fun getInitialCalculation(sentence: Sentence, booking: Booking): SentenceCalculation {

    // create the intermediate values
    val numberOfDaysToSentenceExpiryDate = sentence.duration.getLengthInDays(sentence.sentencedAt)

    val numberOfDaysToReleaseDate =
      ceil(numberOfDaysToSentenceExpiryDate.toDouble().div(TWO)).toInt()

    val unadjustedExpiryDate =
      sentence.sentencedAt
        .plusDays(numberOfDaysToSentenceExpiryDate.toLong())
        .minusDays(ONE)

    val unadjustedReleaseDate =
      sentence.sentencedAt
        .plusDays(numberOfDaysToReleaseDate.toLong())
        .minusDays(ONE)

    val calculatedTotalDeductedDays =
      booking.getOrZero(AdjustmentType.REMAND) + booking.getOrZero(AdjustmentType.TAGGED_BAIL)

    val calculatedTotalAddedDays =
      booking.getOrZero(AdjustmentType.UNLAWFULLY_AT_LARGE)

    var numberOfDaysToAddToLicenceExpiryDate = 0

    val calculatedExpiryTotalDeductedDays =
      if (calculatedTotalDeductedDays >= numberOfDaysToReleaseDate) {
        numberOfDaysToAddToLicenceExpiryDate = calculatedTotalDeductedDays - numberOfDaysToReleaseDate
        numberOfDaysToReleaseDate.toLong()
      } else {
        calculatedTotalDeductedDays.toLong()
      }

    val adjustedExpiryDate = unadjustedExpiryDate
      .minusDays(
        calculatedExpiryTotalDeductedDays
      ).plusDays(
        calculatedTotalAddedDays.toLong()
      )

    val calculatedTotalAwardedDays = max(
      0,
      booking.getOrZero(AdjustmentType.ADDITIONAL_DAYS_AWARDED) -
        booking.getOrZero(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED)
    )

    val adjustedReleaseDate = unadjustedReleaseDate.minusDays(
      calculatedTotalDeductedDays.toLong()
    ).plusDays(
      calculatedTotalAddedDays.toLong()
    ).plusDays(
      calculatedTotalAwardedDays.toLong()
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
      adjustedExpiryDate,
      adjustedReleaseDate,
      numberOfDaysToAddToLicenceExpiryDate
    )
  }



  private fun calculateHDCED(sentence: Sentence, sentenceCalculation: SentenceCalculation) {
    if (sentence.durationIsLessThan(EIGHTEEN, ChronoUnit.MONTHS)) {
      sentenceCalculation.numberOfDaysToHomeDetentionCurfewExpiryDate =
        max(TWENTY_EIGHT, ceil(sentenceCalculation.numberOfDaysToSentenceExpiryDate.toDouble().div(FOUR)).toLong())
          .plus(sentenceCalculation.calculatedTotalAddedDays)
          .minus(sentenceCalculation.calculatedTotalDeductedDays)
          .plus(sentenceCalculation.calculatedTotalAwardedDays)
      sentenceCalculation.homeDetentionCurfewExpiryDateDate = sentence.sentencedAt.plusDays(
        sentenceCalculation.numberOfDaysToHomeDetentionCurfewExpiryDate
      )
    } else {
      sentenceCalculation.numberOfDaysToHomeDetentionCurfewExpiryDate =
        sentenceCalculation.numberOfDaysToReleaseDate.minus(ONE_HUNDRED_AND_THIRTY_FOUR).toLong()
          .plus(sentenceCalculation.calculatedTotalAddedDays)
          .minus(sentenceCalculation.calculatedTotalDeductedDays)
          .plus(sentenceCalculation.calculatedTotalAwardedDays)
      sentenceCalculation.homeDetentionCurfewExpiryDateDate = sentence.sentencedAt.plusDays(
        sentenceCalculation.numberOfDaysToHomeDetentionCurfewExpiryDate
      ).minusDays(ONE)
    }
    // If adjustments make the CRD before sentence date (i.e. a large REMAND days)
    // then we don't need a HDCED date.
    if (sentence.sentencedAt.isAfterOrEqualTo(sentenceCalculation.adjustedReleaseDate)) {
      sentenceCalculation.homeDetentionCurfewExpiryDateDate = null
    } else if (sentence.sentencedAt.plusDays(14).isAfterOrEqualTo(sentenceCalculation.homeDetentionCurfewExpiryDateDate!!)) {
      sentenceCalculation.homeDetentionCurfewExpiryDateDate = sentence.sentencedAt.plusDays(14)
    }
  }

  companion object {
    private const val ONE = 1L
    private const val TWO = 2L
    private const val THREE = 3L
    private const val FOUR = 4L
    private const val TWELVE = 12L
    private const val EIGHTEEN = 18L
    private const val TWENTY_EIGHT = 28L
    private const val YEAR_IN_DAYS = 365
    private const val ONE_HUNDRED_AND_THIRTY_FOUR = 134
  }
}
