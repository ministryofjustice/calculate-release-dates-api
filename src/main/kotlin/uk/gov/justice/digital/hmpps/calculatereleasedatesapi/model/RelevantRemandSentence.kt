package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class RelevantRemandSentence(
  val sequence: Int,
  val sentenceDate: LocalDate,
  val bookingId: Long
)
