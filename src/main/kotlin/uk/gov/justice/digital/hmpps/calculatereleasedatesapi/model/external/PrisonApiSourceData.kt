package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

data class PrisonApiSourceData(
  val sentenceAndOffences: List<SentenceAndOffences>,
  val prisonerDetails: PrisonerDetails,
  val bookingAndSentenceAdjustments: BookingAndSentenceAdjustments,
  val offenderFinePayments: List<OffenderFinePayment> = listOf(),
  val returnToCustodyDate: ReturnToCustodyDate?,
  // TODO This fixedTermTermRecallDetails variable can replace returnToCustodyDate - to be done as tech debt ticket
  val fixedTermRecallDetails: FixedTermRecallDetails? = null
)
