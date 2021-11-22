package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class ConsecutiveSentencePart(
  val lineSequence: Int,
  val caseSequence: Int,
  override val sentenceLength: String,
  override val sentenceLengthDays: Int,
  val consecutiveToLineSequence: Int?,
  val consecutiveToCaseSequence: Int?,
) : SentenceLengthBreakdown
