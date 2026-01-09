package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import java.time.LocalDateTime

data class ComparisonDto(
  val comparisonShortReference: String,
  val criteria: Map<String, Any>,
  val prison: String? = null,
  var comparisonType: ComparisonType,
  val calculatedAt: LocalDateTime,
  val calculatedByUsername: String,
  var comparisonStatus: ComparisonStatus,
  var numberOfPeopleExpected: Long,
  var numberOfPeopleCompared: Long,
  var numberOfPeopleComparisonFailedFor: Long,
  val numberOfMismatches: Long,
) {
  companion object {
    fun from(entity: Comparison, objectMapper: ObjectMapper) = ComparisonDto(
      comparisonShortReference = entity.comparisonShortReference,
      criteria = objectMapper.convertValue(entity.criteria, object : TypeReference<Map<String, Any>>() {}),
      prison = entity.prison,
      comparisonType = entity.comparisonType,
      calculatedAt = entity.calculatedAt,
      calculatedByUsername = entity.calculatedByUsername,
      comparisonStatus = entity.comparisonStatus,
      numberOfPeopleExpected = entity.numberOfPeopleExpected,
      numberOfPeopleCompared = entity.numberOfPeopleCompared,
      numberOfPeopleComparisonFailedFor = entity.numberOfPeopleComparisonFailedFor,
      numberOfMismatches = entity.numberOfMismatches,
    )
  }
}
