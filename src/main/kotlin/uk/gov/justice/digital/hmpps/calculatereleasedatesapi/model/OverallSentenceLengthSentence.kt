package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class OverallSentenceLengthSentence(
  val custodialDuration: OverallSentenceLength,
  val extensionDuration: OverallSentenceLength? = null,
) {

  fun combinedDuration(): Duration {
    if (extensionDuration == null) {
      return custodialDuration.toDuration()
    }
    return custodialDuration.toDuration().appendAll(extensionDuration.toDuration().durationElements)
  }
}
