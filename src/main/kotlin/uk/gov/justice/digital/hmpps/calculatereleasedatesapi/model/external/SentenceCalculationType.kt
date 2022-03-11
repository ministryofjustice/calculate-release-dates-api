package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType.STANDARD_DETERMINATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceType.STANDARD_RECALL

enum class SentenceCalculationType(
  val sentenceType: SentenceType
) {
  ADIMP(STANDARD_DETERMINATE),
  ADIMP_ORA(STANDARD_DETERMINATE),
  YOI(STANDARD_DETERMINATE),
  YOI_ORA(STANDARD_DETERMINATE),
  SEC91_03(STANDARD_DETERMINATE),
  SEC91_03_ORA(STANDARD_DETERMINATE),
  SEC250(STANDARD_DETERMINATE),
  SEC250_ORA(STANDARD_DETERMINATE),
  LR(STANDARD_RECALL),
  LR_ORA(STANDARD_RECALL),
  LR_YOI_ORA(STANDARD_RECALL),
  LR_SEC91_ORA(STANDARD_RECALL),
  LRSEC250_ORA(STANDARD_RECALL)
}
