package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

@JsonDeserialize(using = SDSEarlyReleaseExclusionTypeDeserializer::class)
enum class SDSEarlyReleaseExclusionType {
  SEXUAL,
  VIOLENT,
  DOMESTIC_ABUSE,
  NATIONAL_SECURITY,
  TERRORISM,
  SEXUAL_T3,
  VIOLENT_T3,
  DOMESTIC_ABUSE_T3,
  NATIONAL_SECURITY_T3,
  TERRORISM_T3,
  MURDER_T3,
  NO,
  ;

  fun isSDS40Tranche3Exclusion(): Boolean = this.name.endsWith("_T3")
}

class SDSEarlyReleaseExclusionTypeDeserializer : StdDeserializer<SDSEarlyReleaseExclusionType>(SDSEarlyReleaseExclusionType::class.java) {
  override fun deserialize(jsonParser: JsonParser, ctxt: DeserializationContext): SDSEarlyReleaseExclusionType = SDSEarlyReleaseExclusionType.valueOf(jsonParser.valueAsString)

  override fun getNullValue(ctxt: DeserializationContext): SDSEarlyReleaseExclusionType = SDSEarlyReleaseExclusionType.NO
}
