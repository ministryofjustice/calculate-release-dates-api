package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertyName
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.io.ClassPathResource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfigurations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseSentenceFilter
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

object CalculationParamsTestConfigHelper {
  fun releasePointMultiplierConfigurationForTests(params: String = "calculation-params"): Double = Binder(propertySource(params)).bind("release-point-multipliers", ReleasePointMultipliersConfiguration::class.java).get().earlyReleasePoint

  fun hdcedConfigurationForTests(params: String = "calculation-params"): HdcedConfiguration = Binder(propertySource(params)).bind("hdced", HdcedConfiguration::class.java).get()

  fun ersedConfigurationForTests(params: String = "calculation-params"): ErsedConfiguration = Binder(propertySource(params)).bind("ersed", ErsedConfiguration::class.java).get()

  fun sdsEarlyReleaseTrancheOneDate(params: String = "calculation-params"): LocalDate {
    val configurationProperty = propertySource(params).getConfigurationProperty(ConfigurationPropertyName.of("sds-40-early-release-tranches.tranche-one-date"))
    val date = configurationProperty.value as Date
    return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
  }

  fun sdsEarlyReleaseTrancheTwoDate(params: String = "calculation-params"): LocalDate {
    val configurationProperty = propertySource(params).getConfigurationProperty(ConfigurationPropertyName.of("sds-40-early-release-tranches.tranche-two-date"))
    val date = configurationProperty.value as Date
    return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
  }

  fun sdsEarlyReleaseTrancheThreeDate(params: String = "calculation-params"): LocalDate {
    val configurationProperty = propertySource(params).getConfigurationProperty(ConfigurationPropertyName.of("sds-40-early-release-tranches.tranche-three-date"))
    val date = configurationProperty.value as Date
    return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
  }

  fun earlyReleaseConfigurationsForTests(params: String = "calculation-params"): EarlyReleaseConfigurations = EarlyReleaseConfigurations(
    listOf(
      EarlyReleaseConfiguration(
        releaseMultiplier = releasePointMultiplierConfigurationForTests(params),
        filter = EarlyReleaseSentenceFilter.SDS_40_EXCLUSIONS,
        tranches = listOf(
          EarlyReleaseTrancheConfiguration(
            type = EarlyReleaseTrancheType.SENTENCE_LENGTH,
            date = sdsEarlyReleaseTrancheOneDate(params),
            duration = 5,
            unit = ChronoUnit.YEARS,
            name = SDSEarlyReleaseTranche.TRANCHE_1,
          ),
          EarlyReleaseTrancheConfiguration(
            type = EarlyReleaseTrancheType.FINAL,
            date = sdsEarlyReleaseTrancheTwoDate(params),
            name = SDSEarlyReleaseTranche.TRANCHE_2,
          ),
        ),
      ),
    ),
  )

  private fun propertySource(params: String) = MapConfigurationPropertySource(YamlPropertiesFactoryBean().apply { setResources(ClassPathResource("application-$params.yml")) }.getObject())
}

data class ReleasePointMultipliersConfiguration(val earlyReleasePoint: Double)
