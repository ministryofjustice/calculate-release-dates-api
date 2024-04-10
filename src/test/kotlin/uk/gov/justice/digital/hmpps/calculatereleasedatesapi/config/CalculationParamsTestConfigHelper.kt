package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.io.ClassPathResource

object CalculationParamsTestConfigHelper {

  fun releasePointMultiplierConfigurationForTests(): ReleasePointMultipliersConfiguration {
    return Binder(propertySource()).bind("release-point-multipliers", ReleasePointMultipliersConfiguration::class.java).get()
  }

  fun hdcedConfigurationForTests(): HdcedConfiguration {
    return Binder(propertySource()).bind("hdced", HdcedConfiguration::class.java).get()
  }
  fun ersedConfigurationForTests(): ErsedConfiguration {
    return Binder(propertySource()).bind("ersed", ErsedConfiguration::class.java).get()
  }
  private fun propertySource() = MapConfigurationPropertySource(YamlPropertiesFactoryBean().apply { setResources(ClassPathResource("application-calculation-params.yml")) }.getObject())
}
