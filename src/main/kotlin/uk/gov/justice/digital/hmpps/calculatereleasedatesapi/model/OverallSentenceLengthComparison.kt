package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class OverallSentenceLengthComparison(
  val custodialLength: OverallSentenceLength,
  val licenseLength: OverallSentenceLength?,
  val custodialLengthMatches: Boolean,
  val licenseLengthMatches: Boolean?,
) {
  constructor(
    custodialLength: Duration,
    licenseLength: Duration?,
    custodialLengthMatches: Boolean,
    licenseLengthMatches: Boolean?,
  ) : this(OverallSentenceLength.fromDuration(custodialLength), if (licenseLength != null) OverallSentenceLength.fromDuration(licenseLength) else null, custodialLengthMatches, licenseLengthMatches)
}
