package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class OverallSentenceLengthComparison(
  val custodialLength: Duration,
  val licenseLength: Duration?,
  val custodialLengthMatches: Boolean,
  val licenseLengthMatches: Boolean?,
)
