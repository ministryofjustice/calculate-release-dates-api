package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class SentenceCalculation(
  var sentence: Sentence, // values here are used to store working values
  var numberOfDaysToSentenceExpiryDate: Int,
  val numberOfDaysToReleaseDate: Int,
  val unadjustedExpiryDate: LocalDate,
  val unadjustedReleaseDate: LocalDate,
  val calculatedTotalDeductedDays: Int,
  val calculatedTotalAddedDays: Int,
  val adjustedExpiryDate: LocalDate,
  val adjustedReleaseDate: LocalDate,
  val numberOfDaysToAddToLicenceExpiryDate: Int
) {

  // Non Parole Date (NPD)
  var numberOfDaysToNonParoleDate: Long = 0
  var nonParoleDate: LocalDate? = null

  // Licence Expiry Date (LED)
  var numberOfDaysToLicenceExpiryDate: Long = 0
  var licenceExpiryDate: LocalDate? = null

  var expiryDate: LocalDate? = null
  var releaseDate: LocalDate? = null
  var topUpSupervisionDate: LocalDate? = null
  var isReleaseDateConditional: Boolean = false
}
