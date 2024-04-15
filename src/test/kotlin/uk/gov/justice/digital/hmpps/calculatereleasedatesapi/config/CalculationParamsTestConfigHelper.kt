package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.io.ClassPathResource

object CalculationParamsTestConfigHelper {

  fun releasePointMultiplierConfigurationForTests(params: String = "calculation-params"): ReleasePointMultipliersConfiguration {
    return Binder(propertySource(params)).bind("release-point-multipliers", ReleasePointMultipliersConfiguration::class.java).get()
  }

  fun hdcedConfigurationForTests(params: String = "calculation-params"): HdcedConfiguration {
    return Binder(propertySource(params)).bind("hdced", HdcedConfiguration::class.java).get()
  }
  fun ersedConfigurationForTests(params: String = "calculation-params"): ErsedConfiguration {
    return Binder(propertySource(params)).bind("ersed", ErsedConfiguration::class.java).get()
  }
  private fun propertySource(params: String) = MapConfigurationPropertySource(YamlPropertiesFactoryBean().apply { setResources(ClassPathResource("application-$params.yml")) }.getObject())
}
