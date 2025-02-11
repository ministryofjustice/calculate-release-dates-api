package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BotusSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetentionAndTrainingOrderSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence

enum class SentenceType(val sentenceClazz: Class<out AbstractSentence>? = null) {
  StandardDeterminate(StandardDeterminateSentence::class.java),
  ExtendedDeterminate(ExtendedDeterminateSentence::class.java),
  Sopc(SopcSentence::class.java),
  AFine(AFineSentence::class.java),
  DetentionAndTrainingOrder(DetentionAndTrainingOrderSentence::class.java),
  Botus(BotusSentence::class.java),
  Indeterminate(),
  Unsupported(),
}
