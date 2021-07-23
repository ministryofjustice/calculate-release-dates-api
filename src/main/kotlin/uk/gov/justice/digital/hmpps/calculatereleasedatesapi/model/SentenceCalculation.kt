package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class SentenceCalculation(var sentence: Sentence) {

  // values here are used to store working values
  var numberOfDaysToSentenceExpiryDate: Int = 0
  var numberOfDaysToReleaseDate: Int = 0

  var calculatedTotalRemandDays: Int = 0
  var calculatedExpiryDate: LocalDate? = null
  var calculatedReleaseDate: LocalDate? = null

  // public values here are used to populate final calculation
  var licenceExpiryDate: LocalDate? = null
  var sentenceExpiryDate: LocalDate? = null
  var releaseDate: LocalDate? = null
  var topUpSupervisionDate: LocalDate? = null
  var isReleaseDateConditional: Boolean = false
}
