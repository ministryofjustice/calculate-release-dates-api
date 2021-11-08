package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import java.time.LocalDate

interface SentenceBreakdown : SentenceLengthBreakdown {
  val sentencedAt: LocalDate
  val dates: Map<SentenceType, DateBreakdown>
}
