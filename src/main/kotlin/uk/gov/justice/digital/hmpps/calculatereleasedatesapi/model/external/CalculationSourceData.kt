package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricalTusedData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonApiExternalMovement

data class CalculationSourceData(
  val sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>,
  val prisonerDetails: PrisonerDetails,
  val bookingAndSentenceAdjustments: AdjustmentsSourceData,
  val offenderFinePayments: List<OffenderFinePayment> = listOf(),
  val returnToCustodyDate: ReturnToCustodyDate?,
  // TODO This fixedTermTermRecallDetails variable can replace returnToCustodyDate - to be done as tech debt ticket
  val fixedTermRecallDetails: FixedTermRecallDetails? = null,
  val historicalTusedData: HistoricalTusedData? = null,
  val movements: List<PrisonApiExternalMovement> = emptyList(),
) {
  fun findLatestRevocationDate() = sentenceAndOffences.filter { from(it.sentenceCalculationType).recallType != null }.mapNotNull { it.revocationDates.maxOrNull() }.maxOrNull()
}
