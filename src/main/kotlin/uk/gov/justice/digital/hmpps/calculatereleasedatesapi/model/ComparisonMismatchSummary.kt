package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.databind.JsonNode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate

data class ComparisonMismatchSummary(
  val personId: String,
  val lastName: String?,
  val isValid: Boolean,
  val isMatch: Boolean,
  val validationMessages: List<ValidationMessage>,
  val shortReference: String,
  val misMatchType: MismatchType,
  val sdsSentencesIdentified: JsonNode,
  val hdcedFourPlusDate: LocalDate?,
  val establishment: String?,
)
