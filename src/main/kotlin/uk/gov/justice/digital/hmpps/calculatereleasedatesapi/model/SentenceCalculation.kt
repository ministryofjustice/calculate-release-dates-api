package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class SentenceCalculation(
  var sentence: CalculableSentence, // values here are used to store working values
  var numberOfDaysToSentenceExpiryDate: Int,
  val numberOfDaysToReleaseDate: Int,
  val unadjustedExpiryDate: LocalDate,
  val unadjustedReleaseDate: LocalDate,
  // Remand and Tagged bail
  var calculatedTotalDeductedDays: Int,
  // UAL
  var calculatedTotalAddedDays: Int,
  // ADA - RADA
  var calculatedTotalAwardedDays: Int,
) {

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
    return unadjustedReleaseDate.minusDays(
      calculatedTotalDeductedDays.toLong()
    ).plusDays(
      calculatedTotalAddedDays.toLong()
    ).plusDays(
      calculatedTotalAwardedDays.toLong()
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
    set (value) {
      _licenceExpiryDate = value
    }

  //  Home Detention Curfew Eligibility Date(HDCED)
  var numberOfDaysToHomeDetentionCurfewEligibilityDate: Long = 0
  var homeDetentionCurfewEligibilityDate: LocalDate? = null

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
