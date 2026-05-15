package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.jetbrains.annotations.NotNull
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OperativeSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OperativeSentenceEnvelopeSource
import java.time.LocalDate

@Entity
@Table(name = "operative_sentence_envelope")
data class OperativeSentenceEnvelopeEntity(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @param:NotNull
  val calculationRequestId: Long,

  @param:NotNull
  val envelopeLengthDays: Long,

  @param:NotNull
  val earliestSentenceDate: LocalDate,

  val isPostRecall: Boolean,

  val containsSdsPlusSentence: Boolean,
) {
  fun toOperativeSentenceEnvelope() = OperativeSentenceEnvelope(
    sentenceEnvelopeLengthInDays = envelopeLengthDays,
    earliestSentenceStartDate = earliestSentenceDate,
    isPostRecallSentenceEnvelope = isPostRecall,
    containsAnSDSPlusSentence = containsSdsPlusSentence,
    sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.CRDS,
  )
}
