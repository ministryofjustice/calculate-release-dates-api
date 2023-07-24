package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceSummary

data class Mismatch (
  var isMismatch: Boolean,
  var sentenceSummary: SentenceSummary,
  val calculateReleaseDates: CalculatedReleaseDates? = null,
)