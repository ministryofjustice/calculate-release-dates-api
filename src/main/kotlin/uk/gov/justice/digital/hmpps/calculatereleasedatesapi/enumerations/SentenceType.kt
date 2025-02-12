package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BotusSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetentionAndTrainingOrderSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence

enum class TermType {
  IMPRISONMENT,
  LICENCE,
}

enum class SentenceType(
  val sentenceClazz: Class<out AbstractSentence>? = null,
  val supportedTerms: Set<TermType> = emptySet(),
) {
  StandardDeterminate(StandardDeterminateSentence::class.java, setOf(TermType.IMPRISONMENT)),
  ExtendedDeterminate(ExtendedDeterminateSentence::class.java, setOf(TermType.IMPRISONMENT, TermType.LICENCE)),
  Sopc(SopcSentence::class.java, setOf(TermType.IMPRISONMENT, TermType.LICENCE)),
  AFine(AFineSentence::class.java, setOf(TermType.IMPRISONMENT)),
  DetentionAndTrainingOrder(DetentionAndTrainingOrderSentence::class.java, setOf(TermType.IMPRISONMENT)),
  Botus(BotusSentence::class.java, setOf(TermType.IMPRISONMENT)),
  Indeterminate,
}
