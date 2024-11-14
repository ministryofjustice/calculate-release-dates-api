package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class CustodialPeriod(
  val from: LocalDate,
  val to: LocalDate,
  val sentences: List<CalculableSentence>,
)
