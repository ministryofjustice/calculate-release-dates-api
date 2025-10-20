package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage

data class RecordARecallResult(
  val validationMessages: List<ValidationMessage> = emptyList(),
  val calculatedReleaseDates: CalculatedReleaseDates? = null,
  @JsonIgnore
  val booking: Booking? = null,
)
