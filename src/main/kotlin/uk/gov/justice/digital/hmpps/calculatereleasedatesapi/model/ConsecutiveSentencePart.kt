package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class ConsecutiveSentencePart(
  val lineSequence: Int,
  val caseSequence: Int,
  val caseReference: String? = null,
  val sentenceCalculationType: String,
  val isSDSPlus: Boolean = false,
  override val sentenceLength: String,
  override val sentenceLengthDays: Int,
  val consecutiveToLineSequence: Int?,
  val consecutiveToCaseSequence: Int?,
) : SentenceLengthBreakdown
