package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.LocalDate

interface SentenceBreakdown : SentenceLengthBreakdown {
  val sentencedAt: LocalDate
  val dates: Map<ReleaseDateType, DateBreakdown>
}
