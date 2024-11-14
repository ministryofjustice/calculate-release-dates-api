package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import java.time.LocalDate

data class Booking(
  val offender: Offender,
  val sentences: List<AbstractSentence>,
  val adjustments: Adjustments = Adjustments(),
  // TODO remove and replace with fixedTermRecallDetails
  val returnToCustodyDate: LocalDate? = null,
  val fixedTermRecallDetails: FixedTermRecallDetails? = null,
  val bookingId: Long = -1L,
  val historicalTusedData: HistoricalTusedData? = null,
)
