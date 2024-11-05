package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricalTusedData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements

data class PrisonApiSourceData(
  val sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>,
  val prisonerDetails: PrisonerDetails,
  val bookingAndSentenceAdjustments: BookingAndSentenceAdjustments,
  val offenderFinePayments: List<OffenderFinePayment> = listOf(),
  val fixedTermRecallDetails: FixedTermRecallDetails? = null,
  val historicalTusedData: HistoricalTusedData? = null,
)
