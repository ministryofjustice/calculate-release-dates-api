package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource
import java.time.LocalDate

data class HistoricalTusedData(
  val tused: LocalDate?,
  val historicalTusedSource: HistoricalTusedSource,
)
