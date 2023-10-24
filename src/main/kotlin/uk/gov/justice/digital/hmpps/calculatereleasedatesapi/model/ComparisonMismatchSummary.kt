package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.databind.JsonNode

data class ComparisonMismatchSummary(
  val personId: String,
  val isValid: Boolean,
  val isMatch: Boolean,
  val validationMessages: JsonNode,
  val shortReference: String,
)
