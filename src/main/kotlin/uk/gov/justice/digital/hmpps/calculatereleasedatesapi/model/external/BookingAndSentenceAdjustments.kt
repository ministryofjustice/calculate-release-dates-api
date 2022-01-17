package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

data class BookingAndSentenceAdjustments(
  val bookingAdjustments: List<BookingAdjustments>,
  val sentenceAdjustments: List<SentenceAdjustments>
)
