package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.STANDARD_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence

enum class SentenceCalculationType(
  val recallType: RecallType?,
  val sentenceClazz: Class<out AbstractSentence>
) {
  ADIMP(null, StandardDeterminateSentence::class.java),
  ADIMP_ORA(null, StandardDeterminateSentence::class.java),
  YOI(null, StandardDeterminateSentence::class.java),
  YOI_ORA(null, StandardDeterminateSentence::class.java),
  SEC91_03(null, StandardDeterminateSentence::class.java),
  SEC91_03_ORA(null, StandardDeterminateSentence::class.java),
  SEC250(null, StandardDeterminateSentence::class.java),
  SEC250_ORA(null, StandardDeterminateSentence::class.java),
  LR(STANDARD_RECALL, StandardDeterminateSentence::class.java),
  LR_ORA(STANDARD_RECALL, StandardDeterminateSentence::class.java),
  LR_YOI_ORA(STANDARD_RECALL, StandardDeterminateSentence::class.java),
  LR_SEC91_ORA(STANDARD_RECALL, StandardDeterminateSentence::class.java),
  LRSEC250_ORA(STANDARD_RECALL, StandardDeterminateSentence::class.java),
  _14FTR_ORA(FIXED_TERM_RECALL_14, StandardDeterminateSentence::class.java),
  FTR(FIXED_TERM_RECALL_28, StandardDeterminateSentence::class.java),
  FTR_ORA(FIXED_TERM_RECALL_28, StandardDeterminateSentence::class.java),
  FTR_SCH15(FIXED_TERM_RECALL_28, StandardDeterminateSentence::class.java),
  FTRSCH15_ORA(FIXED_TERM_RECALL_28, StandardDeterminateSentence::class.java),
  FTRSCH18(FIXED_TERM_RECALL_28, StandardDeterminateSentence::class.java),
  FTRSCH18_ORA(FIXED_TERM_RECALL_28, StandardDeterminateSentence::class.java),
  LASPO_AR(null, ExtendedDeterminateSentence::class.java),
  LASPO_DR(null, ExtendedDeterminateSentence::class.java),
  EDS18(null, ExtendedDeterminateSentence::class.java),
  EDS21(null, ExtendedDeterminateSentence::class.java),
  EDSU18(null, ExtendedDeterminateSentence::class.java);


  companion object {
    fun from(sentenceCalculationType: String): SentenceCalculationType? {
      if (sentenceCalculationType == "14FTR_ORA") {
        return _14FTR_ORA
      }
      return try {
        valueOf(sentenceCalculationType)
      } catch (error: IllegalArgumentException) {
        null
      }
    }
  }
}
