package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class OverallSentenceLengthRequest(
  val overallSentenceLength: OverallSentenceLengthSentence,
  val consecutiveSentences: List<OverallSentenceLengthSentence>,
  val concurrentSentences: List<OverallSentenceLengthSentence>,
  val warrantDate: LocalDate,
)
