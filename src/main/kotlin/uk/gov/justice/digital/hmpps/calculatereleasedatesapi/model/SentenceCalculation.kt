package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_SERVED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RECALL_REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RECALL_TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

data class SentenceCalculation(
  var sentence: CalculableSentence, // values here are used to store working values
  val numberOfDaysToSentenceExpiryDate: Int,
  val numberOfDaysToDeterminateReleaseDateDouble: Double,
  val numberOfDaysToDeterminateReleaseDate: Int,
  val unadjustedExpiryDate: LocalDate,
  val unadjustedDeterminateReleaseDate: LocalDate,
  val numberOfDaysToPostRecallReleaseDate: Int?,
  val unadjustedPostRecallReleaseDate: LocalDate?,
  val adjustments: Adjustments,
  var adjustmentsBefore: LocalDate,
  var adjustmentsAfter: LocalDate? = null,
  val returnToCustodyDate: LocalDate? = null
) {

  val calculatedTotalDeductedDays: Int get() {
    val adjustmentTypes: Array<AdjustmentType> = if (sentence.sentenceType === SentenceType.STANDARD_DETERMINATE) {
      arrayOf(REMAND, TAGGED_BAIL)
    } else {
      arrayOf(RECALL_REMAND, RECALL_TAGGED_BAIL)
    }
    return adjustments.getOrZero(*adjustmentTypes, adjustmentsBefore = adjustmentsBefore, adjustmentsAfter = adjustmentsAfter)
  }

  val calculatedTotalAddedDays: Int get() {
    return adjustments.getOrZero(UNLAWFULLY_AT_LARGE, adjustmentsBefore = adjustmentsBefore, adjustmentsAfter = adjustmentsAfter)
  }

  val calculatedFixedTermRecallAddedDays: Int get() {
    return adjustments.getOrZero(UNLAWFULLY_AT_LARGE, adjustmentsBefore = adjustmentsBefore, adjustmentsAfter = returnToCustodyDate)
  }

  val calculatedTotalAwardedDays: Int get() {
    return max(
      0,
      adjustments.getOrZero(ADDITIONAL_DAYS_AWARDED, adjustmentsBefore = adjustmentsBefore, adjustmentsAfter = adjustmentsAfter) -
        adjustments.getOrZero(RESTORATION_OF_ADDITIONAL_DAYS_AWARDED, ADDITIONAL_DAYS_SERVED, adjustmentsBefore = adjustmentsBefore, adjustmentsAfter = adjustmentsAfter)

    )
  }

  val numberOfDaysToAddToLicenceExpiryDate: Int get() {
    if (sentence.sentenceType === SentenceType.STANDARD_DETERMINATE && calculatedTotalDeductedDays >= numberOfDaysToDeterminateReleaseDate) {
      return calculatedTotalDeductedDays - numberOfDaysToDeterminateReleaseDate
    }
    return 0
  }

  val adjustedExpiryDate: LocalDate get() {
    val calculatedExpiryTotalDeductedDays =
      if (sentence.sentenceType === SentenceType.STANDARD_DETERMINATE && calculatedTotalDeductedDays >= numberOfDaysToDeterminateReleaseDate) {
        numberOfDaysToDeterminateReleaseDate.toLong()
      } else {
        calculatedTotalDeductedDays.toLong()
      }

    return unadjustedExpiryDate
      .minusDays(
        calculatedExpiryTotalDeductedDays
      ).plusDays(
        calculatedTotalAddedDays.toLong()
      )
  }

  val adjustedDeterminateReleaseDate: LocalDate get() {
    val date = unadjustedDeterminateReleaseDate.minusDays(
      calculatedTotalDeductedDays.toLong()
    ).plusDays(
      calculatedTotalAddedDays.toLong()
    ).plusDays(
      calculatedTotalAwardedDays.toLong()
    )
    return if (date.isAfter(sentence.sentencedAt)) {
      date
    } else {
      sentence.sentencedAt
    }
  }

  val adjustedPostRecallReleaseDate: LocalDate? get() {
    if (sentence.sentenceType == SentenceType.STANDARD_RECALL) {
      return unadjustedPostRecallReleaseDate?.minusDays(
        calculatedTotalDeductedDays.toLong()
      )?.plusDays(
        calculatedTotalAddedDays.toLong()
      )
    }
    // Fixed term recalls only apply adjustments from return to custody date
    return unadjustedPostRecallReleaseDate?.plusDays(
      calculatedFixedTermRecallAddedDays.toLong()
    )
  }

  // Non Parole Date (NPD)
  var numberOfDaysToNonParoleDate: Long = 0
  var nonParoleDate: LocalDate? = null

  // Licence Expiry Date (LED)
  var numberOfDaysToLicenceExpiryDate: Long = 0
  private var _licenceExpiryDate: LocalDate? = null
  var licenceExpiryDate: LocalDate?
    get() {
      return if (sentence.releaseDateTypes.contains(ReleaseDateType.SLED)) {
        expiryDate
      } else {
        _licenceExpiryDate
      }
    }
    set(value) {
      _licenceExpiryDate = value
    }

  //  Home Detention Curfew Eligibility Date(HDCED)
  var numberOfDaysToHomeDetentionCurfewEligibilityDate: Long = 0
  var homeDetentionCurfewEligibilityDate: LocalDate? = null
  var breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown> = mutableMapOf()

  // Notional Conditional Release Date (NCRD)
  var numberOfDaysToNotionalConditionalReleaseDate: Long = 0
  var notionalConditionalReleaseDate: LocalDate? = null

  val releaseDate: LocalDate get() {
    return if (sentence.sentenceType.isRecall) {
      adjustedPostRecallReleaseDate!!
    } else {
      adjustedDeterminateReleaseDate
    }
  }

  val expiryDate: LocalDate? get() {
    if (sentence.releaseDateTypes.contains(ReleaseDateType.SLED) || sentence.releaseDateTypes.contains(ReleaseDateType.SED)) {
      return adjustedExpiryDate
    }
    return null
  }
  var topUpSupervisionDate: LocalDate? = null
  var isReleaseDateConditional: Boolean = false

  fun buildString(releaseDateTypes: List<ReleaseDateType>): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val expiryDateType = if (releaseDateTypes.contains(ReleaseDateType.SLED)) "SLED" else "SED"
    val releaseDateType = if (releaseDateTypes.contains(ReleaseDateType.ARD)) "ARD"
    else if (releaseDateTypes.contains(ReleaseDateType.CRD)) "CRD"
    else "PED"

    return "Date of $expiryDateType\t:\t${unadjustedExpiryDate.format(formatter)}\n" +
      "Number of days to $releaseDateType\t:\t${numberOfDaysToDeterminateReleaseDate}\n" +
      "Date of $releaseDateType\t:\t${unadjustedDeterminateReleaseDate.format(formatter)}\n" +
      "Total number of days of deducted (remand / tagged bail)\t:" +
      "\t${calculatedTotalDeductedDays}\n" +
      "Total number of days of added (UAL) \t:\t${calculatedTotalAddedDays}\n" +
      "Total number of days of awarded (ADA / RADA) \t:\t${calculatedTotalAwardedDays}\n" +

      "Total number of days to Licence Expiry Date (LED)\t:\t${numberOfDaysToLicenceExpiryDate}\n" +
      "LED\t:\t${licenceExpiryDate?.format(formatter)}\n" +

      "Number of days to Non Parole Date (NPD)\t:\t${numberOfDaysToNonParoleDate}\n" +
      "Non Parole Date (NPD)\t:\t${nonParoleDate?.format(formatter)}\n" +

      "Number of days to Home Detention Curfew Eligibility Date (HDCED)\t:\t" +
      "${numberOfDaysToHomeDetentionCurfewEligibilityDate}\n" +
      "Home Detention Curfew Eligibility Date (HDCED)\t:\t" +
      "${homeDetentionCurfewEligibilityDate?.format(formatter)}\n" +

      "Effective $expiryDateType\t:\t${expiryDate?.format(formatter)}\n" +
      "Effective $releaseDateType\t:\t${releaseDate?.format(formatter)}\n" +
      "Top-up Expiry Date (Post Sentence Supervision PSS)\t:\t" +
      "${topUpSupervisionDate?.format(formatter)}\n"
  }
}
