package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.ADIMP
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.ADIMP_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.SEC250
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.SEC250_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.SEC91_03
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.SEC91_03_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.YOI
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.YOI_ORA

fun String.isSdsCalcType() = listOf(
  ADIMP.name,
  ADIMP_ORA.name,
  SEC91_03.name,
  SEC91_03_ORA.name,
  SEC250.name,
  SEC250_ORA.name,
  YOI.name,
  YOI_ORA.name,
).contains(this)
