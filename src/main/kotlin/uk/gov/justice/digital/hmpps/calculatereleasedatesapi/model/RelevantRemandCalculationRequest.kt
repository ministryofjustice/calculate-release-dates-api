package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class RelevantRemandCalculationRequest(
  val relevantRemands: List<RelevantRemand>,
  val sentence: RelevantRemandSentence,
)
