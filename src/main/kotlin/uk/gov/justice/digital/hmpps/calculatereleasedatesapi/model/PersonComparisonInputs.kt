package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto

data class PersonComparisonInputs(
  val inputData: Booking,
  val sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>,
  val adjustments: List<AdjustmentDto>,
)
