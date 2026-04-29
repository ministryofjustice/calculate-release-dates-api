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
) {
  SEXUAL(sds40Exclusion = true, sds40AdditionalExcludedOffence = false, progressionModelExclusion = false),
  VIOLENT(sds40Exclusion = true, sds40AdditionalExcludedOffence = false, progressionModelExclusion = false),
  DOMESTIC_ABUSE(sds40Exclusion = true, sds40AdditionalExcludedOffence = false, progressionModelExclusion = false),
  NATIONAL_SECURITY(sds40Exclusion = true, sds40AdditionalExcludedOffence = false, progressionModelExclusion = false),
  TERRORISM(sds40Exclusion = true, sds40AdditionalExcludedOffence = false, progressionModelExclusion = false),
  SEXUAL_T3(sds40Exclusion = false, sds40AdditionalExcludedOffence = true, progressionModelExclusion = false),
  DOMESTIC_ABUSE_T3(sds40Exclusion = false, sds40AdditionalExcludedOffence = true, progressionModelExclusion = false),
  NATIONAL_SECURITY_T3(sds40Exclusion = false, sds40AdditionalExcludedOffence = true, progressionModelExclusion = false),
  TERRORISM_T3(sds40Exclusion = false, sds40AdditionalExcludedOffence = true, progressionModelExclusion = false),
  MURDER_T3(sds40Exclusion = false, sds40AdditionalExcludedOffence = true, progressionModelExclusion = false),
  SCHEDULE_13_PART_3(sds40Exclusion = false, sds40AdditionalExcludedOffence = false, progressionModelExclusion = true),
  NO(sds40Exclusion = false, sds40AdditionalExcludedOffence = false, progressionModelExclusion = false),
}

class SDSEarlyReleaseExclusionTypeDeserializer : StdDeserializer<SDSEarlyReleaseExclusionType>(SDSEarlyReleaseExclusionType::class.java) {
  override fun deserialize(jsonParser: JsonParser, ctxt: DeserializationContext): SDSEarlyReleaseExclusionType = SDSEarlyReleaseExclusionType.valueOf(jsonParser.valueAsString)

  override fun getNullValue(ctxt: DeserializationContext): SDSEarlyReleaseExclusionType = SDSEarlyReleaseExclusionType.NO
}
