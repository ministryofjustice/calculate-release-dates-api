package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.STANDARD_RECALL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence

enum class SentenceCalculationType(
  val recallType: RecallType? = null,
  val sentenceClazz: Class<out AbstractSentence> = StandardDeterminateSentence::class.java
) {
  ADIMP,
  ADIMP_ORA,
  YOI,
  YOI_ORA,
  SEC91_03,
  SEC91_03_ORA,
  SEC250,
  SEC250_ORA,
  LR(STANDARD_RECALL),
  LR_ORA(STANDARD_RECALL),
  LR_YOI_ORA(STANDARD_RECALL),
  LR_SEC91_ORA(STANDARD_RECALL),
  LRSEC250_ORA(STANDARD_RECALL),
  _14FTR_ORA(FIXED_TERM_RECALL_14),
  FTR(FIXED_TERM_RECALL_28),
  FTR_ORA(FIXED_TERM_RECALL_28),
  FTR_SCH15(FIXED_TERM_RECALL_28),
  FTRSCH15_ORA(FIXED_TERM_RECALL_28),
  FTRSCH18(FIXED_TERM_RECALL_28),
  FTRSCH18_ORA(FIXED_TERM_RECALL_28),
  LASPO_AR(sentenceClazz = ExtendedDeterminateSentence::class.java),
  LASPO_DR(sentenceClazz = ExtendedDeterminateSentence::class.java),
  EDS18(sentenceClazz = ExtendedDeterminateSentence::class.java),
  EDS21(sentenceClazz = ExtendedDeterminateSentence::class.java),
  EDSU18(sentenceClazz = ExtendedDeterminateSentence::class.java),
  SDOPCU18(sentenceClazz = SopcSentence::class.java),
  SOPC18(sentenceClazz = SopcSentence::class.java),
  SOPC21(sentenceClazz = SopcSentence::class.java);

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
