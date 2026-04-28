package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto

data class PersonComparisonInputs(
  val inputData: Booking,
  val sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangementsV4>,
  val adjustments: List<AdjustmentDto>,
)
