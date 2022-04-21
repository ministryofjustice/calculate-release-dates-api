package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_14
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.FIXED_TERM_RECALL_28
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType.STANDARD_RECALL

enum class SentenceCalculationType(
  val recallType: RecallType?
) {
  ADIMP(null),
  ADIMP_ORA(null),
  YOI(null),
  YOI_ORA(null),
  SEC91_03(null),
  SEC91_03_ORA(null),
  SEC250(null),
  SEC250_ORA(null),
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
  FTRSCH18_ORA(FIXED_TERM_RECALL_28);

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
