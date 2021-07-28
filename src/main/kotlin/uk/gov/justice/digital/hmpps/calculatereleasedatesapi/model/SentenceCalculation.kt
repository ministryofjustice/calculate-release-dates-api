package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class SentenceCalculation(
  var sentence: Sentence, // values here are used to store working values
  var numberOfDaysToSentenceExpiryDate: Int,
  val numberOfDaysToReleaseDate: Int,
  val unadjustedExpiryDate: LocalDate,
  val unadjustedReleaseDate: LocalDate,
  val calculatedTotalRemandDays: Int,
  val remandAdjustedExpiryDate: LocalDate,
  val remandAdjustedReleaseDate: LocalDate
) {

  var nonParoleDate: LocalDate? = null

  // public values here are used to populate final calculation
  var licenceExpiryDate: LocalDate? = null
  var expiryDate: LocalDate? = null
  var releaseDate: LocalDate? = null
  var topUpSupervisionDate: LocalDate? = null
  var isReleaseDateConditional: Boolean = false
}
