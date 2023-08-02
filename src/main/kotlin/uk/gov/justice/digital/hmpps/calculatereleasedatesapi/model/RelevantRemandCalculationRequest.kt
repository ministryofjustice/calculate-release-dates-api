package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

data class RelevantRemandCalculationRequest(
  val relevantRemands: List<RelevantRemand>,
  val sentence: RelevantRemandSentence,
  val calculateAt: LocalDate,
)
