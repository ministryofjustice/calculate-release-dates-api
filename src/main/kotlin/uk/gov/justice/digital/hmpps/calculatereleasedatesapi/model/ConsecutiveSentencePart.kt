package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class ConsecutiveSentencePart(
  val sequence: String,
  override val sentenceLength: String,
  override val sentenceLengthDays: Int,
  val consecutiveTo: String? = null
) : SentenceLengthBreakdown
