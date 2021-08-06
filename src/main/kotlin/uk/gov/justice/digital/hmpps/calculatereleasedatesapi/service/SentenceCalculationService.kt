package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

@Service
class SentenceCalculationService {
  fun identify(sentence: Sentence, offender: Offender) {
    if (
      sentence.sentencedAt.isBefore(ImportantDates.LASPO_DATE) &&
      sentence.offence.startedAt.isBefore(ImportantDates.CJA_DATE)
    ) {
      beforeCJAAndLASPO(sentence)
    } else {
      afterCJAAndLASPO(sentence, offender)
    }
  }

  private fun afterCJAAndLASPO(sentence: Sentence, offender: Offender) {

    if (!sentence.durationIsGreaterThanOrEqualTo(TWELVE, ChronoUnit.MONTHS)) {

      if (sentence.offence.startedAt.isAfter(ImportantDates.ORA_DATE)) {
        isTopUpSentenceExpiryDateRequired(sentence, offender)
      } else {
        sentence.sentenceTypes = listOf(
          SentenceType.ARD,
          SentenceType.SED
        )
      }
    } else {
      if (sentence.durationIsGreaterThan(TWO, ChronoUnit.YEARS)) {
        sentence.sentenceTypes = listOf(
          SentenceType.SLED,
          SentenceType.CRD
        )
      } else {

        if (sentence.offence.startedAt.isBefore(ImportantDates.ORA_DATE)) {
          sentence.sentenceTypes = listOf(
            SentenceType.SLED,
            SentenceType.CRD
          )
        } else {
          isTopUpSentenceExpiryDateRequired(sentence, offender)
        }
      }
    }
  }

  private fun beforeCJAAndLASPO(sentence: Sentence) {
    if (sentence.durationIsGreaterThan(FOUR, ChronoUnit.YEARS)) {
      if (! sentence.offence.isScheduleFifteen) {
        sentence.sentenceTypes = listOf(
          SentenceType.PED,
          SentenceType.NPD,
          SentenceType.LED,
          SentenceType.SED
        )
      } else {
        sentence.sentenceTypes = listOf(
          SentenceType.CRD,
          SentenceType.SLED
        )
      }
    } else if (sentence.durationIsGreaterThanOrEqualTo(TWELVE, ChronoUnit.MONTHS)) {
      sentence.sentenceTypes = listOf(
        SentenceType.LED,
        SentenceType.CRD,
        SentenceType.SED
      )
    } else {
      sentence.sentenceTypes = listOf(
        SentenceType.ARD,
        SentenceType.SED
      )
    }
  }

  private fun isTopUpSentenceExpiryDateRequired(sentence: Sentence, offender: Offender) {
    if (
      sentence.duration.getLengthInDays(sentence.sentencedAt) <= INT_ONE ||
      offender.getAgeOnDate(sentence.getHalfSentenceDate()) <= INT_EIGHTEEN
    ) {
      sentence.sentenceTypes = listOf(SentenceType.SLED, SentenceType.CRD)
    } else {
      sentence.sentenceTypes = listOf(
        SentenceType.SLED,
        SentenceType.CRD,
        SentenceType.TUSED
      )
    }
  }

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

    if (sentence.sentenceTypes.contains(SentenceType.TUSED)) {
      sentenceCalculation.topUpSupervisionDate = sentenceCalculation.adjustedReleaseDate
        .plus(TWELVE, ChronoUnit.MONTHS)
    }

    if (sentence.sentenceTypes.contains(SentenceType.PED)) {
      sentenceCalculation.topUpSupervisionDate = sentenceCalculation.adjustedReleaseDate
        .plus(TWELVE, ChronoUnit.MONTHS)
    }

    if (sentence.sentenceTypes.contains(SentenceType.NPD)) {
      sentenceCalculation.nonParoleDate = sentence.sentencedAt.plusDays(
        ceil(sentenceCalculation.numberOfDaysToSentenceExpiryDate.toDouble().times(TWO.div(THREE))).toLong()
      )
    }

    if (sentence.sentenceTypes.contains(SentenceType.LED)) {
      sentenceCalculation.nonParoleDate = sentence.sentencedAt.plusDays(
        ceil(sentenceCalculation.numberOfDaysToSentenceExpiryDate.toDouble().times(THREE.div(FOUR))).toLong()
      )
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

    val adjustedExpiryDate = unadjustedExpiryDate
      .minusDays(
        calculatedTotalDeductedDays.toLong()
      ).plusDays(
        calculatedTotalAddedDays.toLong()
      )

    val adjustedReleaseDate = unadjustedReleaseDate.minusDays(
      calculatedTotalDeductedDays.toLong()
    ).plusDays(
      calculatedTotalAddedDays.toLong()
    )

    // create new SentenceCalculation and associate it with a sentence
    return SentenceCalculation(
      sentence,
      numberOfDaysToSentenceExpiryDate,
      numberOfDaysToReleaseDate,
      unadjustedExpiryDate,
      unadjustedReleaseDate,
      calculatedTotalDeductedDays,
      adjustedExpiryDate,
      adjustedReleaseDate
    )
  }

  companion object {
    private const val ONE = 1L
    private const val TWO = 2L
    private const val THREE = 3L
    private const val FOUR = 4L
    private const val TWELVE = 12L
    private const val INT_EIGHTEEN = 18
    private const val INT_ONE = 1
  }
}
