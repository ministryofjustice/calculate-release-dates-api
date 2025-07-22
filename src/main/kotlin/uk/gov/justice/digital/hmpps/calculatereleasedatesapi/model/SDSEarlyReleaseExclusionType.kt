package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

@JsonDeserialize(using = SDSEarlyReleaseExclusionTypeDeserializer::class)
enum class SDSEarlyReleaseExclusionType(
  val trancheThreeExclusion: Boolean = false,
) {
  SEXUAL,
  VIOLENT,
  DOMESTIC_ABUSE,
  NATIONAL_SECURITY,
  TERRORISM,
  SEXUAL_T3(true),
  VIOLENT_T3(true),
  DOMESTIC_ABUSE_T3(true),
  NATIONAL_SECURITY_T3(true),
  TERRORISM_T3(true),
  MURDER_T3(true),
  NO,
}

class SDSEarlyReleaseExclusionTypeDeserializer : StdDeserializer<SDSEarlyReleaseExclusionType>(SDSEarlyReleaseExclusionType::class.java) {
  override fun deserialize(jsonParser: JsonParser, ctxt: DeserializationContext): SDSEarlyReleaseExclusionType = SDSEarlyReleaseExclusionType.valueOf(jsonParser.valueAsString)

  override fun getNullValue(ctxt: DeserializationContext): SDSEarlyReleaseExclusionType = SDSEarlyReleaseExclusionType.NO
}
