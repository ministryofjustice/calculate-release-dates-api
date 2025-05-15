package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
@ConfigurationPropertiesBinding
class LocalDatePropertyConverter : Converter<String, LocalDate> {
  override fun convert(source: String): LocalDate? = if (source == "") null else LocalDate.parse(source)
}
