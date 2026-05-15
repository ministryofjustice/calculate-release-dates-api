package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema
data class OperativeSentenceEnvelope(
  @param:Schema(description = "The length in days of the sentence envelope")
  val sentenceEnvelopeLengthInDays: Long,
  @param:Schema(description = "The date of the earliest sentence in the envelope")
  val earliestSentenceStartDate: LocalDate,
  @param:Schema(description = "Whether this is for a post recall sentence envelope. Null indicates this is unknown.")
  val isPostRecallSentenceEnvelope: Boolean?,
  @param:Schema(description = "Whether the sentence envelope contains an SDS plus sentence or not. Null indicates this is unknown.")
  val containsAnSDSPlusSentence: Boolean?,
  @param:Schema(description = "The source of the data used to determine the operative sentence envelope")
  val sentenceEnvelopeSource: OperativeSentenceEnvelopeSource,
)
