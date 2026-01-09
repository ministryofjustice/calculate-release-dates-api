package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertyName
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.io.ClassPathResource
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

object CalculationParamsTestConfigHelper {

  fun hdcedConfigurationForTests(params: String = "calculation-params"): HdcedConfiguration = Binder(propertySource(params)).bind("hdced", HdcedConfiguration::class.java).get()

  fun sdsEarlyReleaseTrancheOneDate(params: String = "calculation-params"): LocalDate {
    val configurationProperty = propertySource(params).getConfigurationProperty(ConfigurationPropertyName.of("sds-40-early-release-tranches.tranche-one-date"))!!
    val date = configurationProperty.value as Date
    return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
  }

  fun sdsEarlyReleaseTrancheTwoDate(params: String = "calculation-params"): LocalDate {
    val configurationProperty = propertySource(params).getConfigurationProperty(ConfigurationPropertyName.of("sds-40-early-release-tranches.tranche-two-date"))!!
    val date = configurationProperty.value as Date
    return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
  }

  fun sdsEarlyReleaseTrancheThreeDate(params: String = "calculation-params"): LocalDate {
    val configurationProperty = propertySource(params).getConfigurationProperty(ConfigurationPropertyName.of("sds-40-early-release-tranches.tranche-three-date"))!!
    val date = configurationProperty.value as Date
    return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
  }

  private fun propertySource(params: String) = MapConfigurationPropertySource(YamlPropertiesFactoryBean().apply { setResources(ClassPathResource("application-$params.yml")) }.getObject()!!.toMap())
}

data class ReleasePointMultipliersConfiguration(val earlyReleasePoint: Double)
