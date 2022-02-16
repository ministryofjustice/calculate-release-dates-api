package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

data class PrisonApiSourceData(
  val sentenceAndOffences: List<SentenceAndOffences>,
  val prisonerDetails: PrisonerDetails,
  val bookingAndSentenceAdjustments: BookingAndSentenceAdjustments
)
