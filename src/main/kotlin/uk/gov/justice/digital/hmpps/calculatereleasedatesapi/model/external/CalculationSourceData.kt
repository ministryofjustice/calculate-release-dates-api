package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import arrow.core.Either
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricalTusedData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonApiExternalMovement

data class CalculationSourceData(
  val sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>,
  val prisonerDetails: PrisonerDetails,
  val bookingAndSentenceAdjustments: Either<BookingAndSentenceAdjustments, List<AdjustmentDto>>,
  val offenderFinePayments: List<OffenderFinePayment> = listOf(),
  val returnToCustodyDate: ReturnToCustodyDate?,
  // TODO This fixedTermTermRecallDetails variable can replace returnToCustodyDate - to be done as tech debt ticket
  val fixedTermRecallDetails: FixedTermRecallDetails? = null,
  val historicalTusedData: HistoricalTusedData? = null,
  val movements: List<PrisonApiExternalMovement> = emptyList(),
)
