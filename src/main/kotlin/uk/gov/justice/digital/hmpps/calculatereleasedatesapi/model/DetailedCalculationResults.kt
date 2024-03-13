package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences

data class DetailedCalculationResults(
  val calculationRequestId: Long,
  val dates: Map<ReleaseDateType, DetailedReleaseDate>,
  val sentencesAndOffences: List<SentenceAndOffences>?,
  val calculationBreakdown: CalculationBreakdown?,
  val breakdownMissingReason: BreakdownMissingReason? = null,
)
