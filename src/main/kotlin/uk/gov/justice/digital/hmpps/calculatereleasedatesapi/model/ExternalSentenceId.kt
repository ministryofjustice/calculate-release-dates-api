package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class ExternalSentenceId(
  val sentenceSequence: Int,
  val bookingId: Long,
)
