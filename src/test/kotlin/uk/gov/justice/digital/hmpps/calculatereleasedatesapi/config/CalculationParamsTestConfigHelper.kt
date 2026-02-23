package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.io.ClassPathResource

object CalculationParamsTestConfigHelper {
  fun hdcedConfigurationForTests(params: String = "calculation-params"): HdcedConfiguration = Binder(propertySource(params)).bind("hdced", HdcedConfiguration::class.java).get()
  private fun propertySource(params: String) = MapConfigurationPropertySource(YamlPropertiesFactoryBean().apply { setResources(ClassPathResource("application-$params.yml")) }.getObject()!!.toMap())
}
