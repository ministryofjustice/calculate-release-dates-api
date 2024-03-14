package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences

data class DetailedCalculationResults(
  val context: CalculationContext,
  val dates: Map<ReleaseDateType, DetailedDate>,
  val approvedDates: Map<ReleaseDateType, DetailedDate>?,
  val prisonerDetails: PrisonerDetails?,
  val sentencesAndOffences: List<SentenceAndOffences>?,
  val calculationBreakdown: CalculationBreakdown?,
  val breakdownMissingReason: BreakdownMissingReason? = null,
)
