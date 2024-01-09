package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate

data class ComparisonMismatchSummary(
  val personId: String,
  val isValid: Boolean,
  val isMatch: Boolean,
  val validationMessages: JsonNode,
  val shortReference: String,
  val misMatchType: MismatchType,
  val sdsSentencesIdentified: JsonNode,
  val hdcedFourPlusDate: LocalDate?,
)
