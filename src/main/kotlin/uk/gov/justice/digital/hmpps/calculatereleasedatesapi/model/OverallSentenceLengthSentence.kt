package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class OverallSentenceLengthSentence(
  val custodialDuration: Duration,
  val extensionDuration: Duration? = null,
) {

  fun combinedDuration(): Duration {
    if (extensionDuration == null) {
      return custodialDuration
    }
    return custodialDuration.appendAll(extensionDuration.durationElements)
  }
}
