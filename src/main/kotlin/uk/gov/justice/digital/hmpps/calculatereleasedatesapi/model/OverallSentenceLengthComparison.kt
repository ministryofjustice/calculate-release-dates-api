package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class OverallSentenceLengthComparison(
  val custodialLength: OverallSentenceLength,
  val licenceLength: OverallSentenceLength?,
  val custodialLengthMatches: Boolean,
  val licenceLengthMatches: Boolean?,
) {
  constructor(
    custodialLength: Duration,
    licenceLength: Duration?,
    custodialLengthMatches: Boolean,
    licenceLengthMatches: Boolean?,
  ) : this(OverallSentenceLength.fromDuration(custodialLength), if (licenceLength != null) OverallSentenceLength.fromDuration(licenceLength) else null, custodialLengthMatches, licenceLengthMatches)
}
