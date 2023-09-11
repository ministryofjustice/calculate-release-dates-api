package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate

data class RelevantRemandCalculationResult(
  val releaseDate: LocalDate? = null,
  val postRecallReleaseDate: LocalDate? = null,
  val unusedDeductions: Int = 0,
  val validationMessages: List<ValidationMessage> = emptyList(),
)
