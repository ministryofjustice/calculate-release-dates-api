package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

@JsonDeserialize(using = SDSEarlyReleaseExclusionTypeDeserializer::class)
enum class SDSEarlyReleaseExclusionType(
  val sds40Exclusion: Boolean,
  val sds40AdditionalExcludedOffence: Boolean,
  val progressionModelExclusion: Boolean,
  val displayName: String?,
) {
  SEXUAL(sds40Exclusion = true, sds40AdditionalExcludedOffence = false, progressionModelExclusion = false, displayName = "Sexual"),
  VIOLENT(sds40Exclusion = true, sds40AdditionalExcludedOffence = false, progressionModelExclusion = false, displayName = "Violent"),
  DOMESTIC_ABUSE(sds40Exclusion = true, sds40AdditionalExcludedOffence = false, progressionModelExclusion = false, displayName = "Domestic Abuse"),
  NATIONAL_SECURITY(sds40Exclusion = true, sds40AdditionalExcludedOffence = false, progressionModelExclusion = false, displayName = "National Security"),
  TERRORISM(sds40Exclusion = true, sds40AdditionalExcludedOffence = false, progressionModelExclusion = false, displayName = "Terrorism"),
  SEXUAL_T3(sds40Exclusion = false, sds40AdditionalExcludedOffence = true, progressionModelExclusion = false, displayName = "Sexual (for prisoners in custody on or after the 16th Dec 2024)"),
  DOMESTIC_ABUSE_T3(sds40Exclusion = false, sds40AdditionalExcludedOffence = true, progressionModelExclusion = false, displayName = "Domestic Abuse (for prisoners in custody on or after the 16th Dec 2024)"),
  MURDER_T3(sds40Exclusion = false, sds40AdditionalExcludedOffence = true, progressionModelExclusion = false, displayName = "Murder (for prisoners in custody on or after the 16th Dec 2024)"),
  PROGRESSION_MODEL_SCHEDULE_13_PART_3(sds40Exclusion = false, sds40AdditionalExcludedOffence = false, progressionModelExclusion = true, displayName = "Schedule 13 Part 3"),
  PROGRESSION_MODEL_OTHER_THING(sds40Exclusion = false, sds40AdditionalExcludedOffence = false, progressionModelExclusion = true, displayName = "Other thing"),
  NO(sds40Exclusion = false, sds40AdditionalExcludedOffence = false, progressionModelExclusion = false, displayName = null),
}

class SDSEarlyReleaseExclusionTypeDeserializer : StdDeserializer<SDSEarlyReleaseExclusionType>(SDSEarlyReleaseExclusionType::class.java) {
  override fun deserialize(jsonParser: JsonParser, ctxt: DeserializationContext): SDSEarlyReleaseExclusionType = SDSEarlyReleaseExclusionType.valueOf(jsonParser.valueAsString)

  override fun getNullValue(ctxt: DeserializationContext): SDSEarlyReleaseExclusionType = SDSEarlyReleaseExclusionType.NO
}
