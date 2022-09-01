package uk.gov.justice.digital.hmpps.calculatereleasedatesapi

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.text.SimpleDateFormat

class TestUtil private constructor() {
  companion object {
    fun objectMapper(): ObjectMapper {
      val mapper = ObjectMapper()
      mapper.registerModule(JavaTimeModule())
      mapper.dateFormat = SimpleDateFormat("yyyy-MM-dd")
      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      return mapper.registerKotlinModule()
    }
  }
}
