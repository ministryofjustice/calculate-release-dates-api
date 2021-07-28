package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class OffenderSentenceProfileCalculation(
  var licenceExpiryDate: LocalDate?,
  var sentenceExpiryDate: LocalDate?,
  var releaseDate: LocalDate?,
  var topUpSupervisionDate: LocalDate?,
  var isReleaseDateConditional: Boolean
)
