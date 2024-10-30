package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.databind.JsonNode

data class PersonComparisonJson(
  val inputData: JsonNode,
  val sentenceAndOffences: JsonNode?,
  val adjustments: JsonNode?,
)
