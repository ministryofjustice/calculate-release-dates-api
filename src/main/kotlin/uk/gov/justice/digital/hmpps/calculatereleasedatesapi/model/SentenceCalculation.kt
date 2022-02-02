package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

data class SentenceCalculation(
  var sentence: CalculableSentence, // values here are used to store working values
  var numberOfDaysToSentenceExpiryDate: Int,
  val numberOfDaysToReleaseDateDouble: Double,
  val numberOfDaysToReleaseDate: Int,
  val unadjustedExpiryDate: LocalDate,
  val unadjustedReleaseDate: LocalDate,
  val adjustments: Adjustments,
  var adjustmentsBefore: LocalDate,
  var adjustmentsAfter: LocalDate? = null
) {

  val calculatedTotalDeductedDays: Int get() {
    return adjustments.getOrZero(AdjustmentType.REMAND, AdjustmentType.TAGGED_BAIL, adjustmentsBefore = adjustmentsBefore, adjustmentsAfter = adjustmentsAfter)
  }

  val calculatedTotalAddedDays: Int get() {
    return adjustments.getOrZero(AdjustmentType.UNLAWFULLY_AT_LARGE, adjustmentsBefore = adjustmentsBefore, adjustmentsAfter = adjustmentsAfter)
  }

  val calculatedTotalAwardedDays: Int get() {
    return max(
      0,
      adjustments.getOrZero(AdjustmentType.ADDITIONAL_DAYS_AWARDED, adjustmentsBefore = adjustmentsBefore, adjustmentsAfter = adjustmentsAfter) -
        adjustments.getOrZero(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED, AdjustmentType.ADDITIONAL_DAYS_SERVED, adjustmentsBefore = adjustmentsBefore, adjustmentsAfter = adjustmentsAfter)

    )
  }

  val numberOfDaysToAddToLicenceExpiryDate: Int get() {
    if (calculatedTotalDeductedDays >= numberOfDaysToReleaseDate) {
      return calculatedTotalDeductedDays - numberOfDaysToReleaseDate
    }
    return 0
  }

  val adjustedExpiryDate: LocalDate get() {
    val calculatedExpiryTotalDeductedDays =
      if (calculatedTotalDeductedDays >= numberOfDaysToReleaseDate) {
        numberOfDaysToReleaseDate.toLong()
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

  val adjustedReleaseDate: LocalDate get() {
    val date = unadjustedReleaseDate.minusDays(
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

  val releaseDate: LocalDate? get() {
    if (
      sentence.releaseDateTypes.contains(ReleaseDateType.CRD) ||
      sentence.releaseDateTypes.contains(ReleaseDateType.ARD) ||
      sentence.releaseDateTypes.contains(ReleaseDateType.PED)
    ) {
      return adjustedReleaseDate
    }
    return null
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
      "Number of days to $releaseDateType\t:\t${numberOfDaysToReleaseDate}\n" +
      "Date of $releaseDateType\t:\t${unadjustedReleaseDate.format(formatter)}\n" +
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
