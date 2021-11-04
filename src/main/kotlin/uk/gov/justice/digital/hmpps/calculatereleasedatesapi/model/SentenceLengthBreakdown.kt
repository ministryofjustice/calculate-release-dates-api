package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

interface SentenceLengthBreakdown {
  val sentenceLength: String
  val sentenceLengthDays: Int
}
