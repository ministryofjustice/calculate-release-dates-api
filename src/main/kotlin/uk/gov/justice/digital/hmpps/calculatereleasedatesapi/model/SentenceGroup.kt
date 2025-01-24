package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

/*
  Represents a group of sentences, where the custodial periods were considered concurrent and overlapping.
 */
data class SentenceGroup(
  val from: LocalDate,
  val to: LocalDate,
  val sentences: List<CalculableSentence>,
)
